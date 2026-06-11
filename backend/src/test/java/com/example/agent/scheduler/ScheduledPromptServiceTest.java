package com.example.agent.scheduler;

import com.example.agent.AgentService;
import com.example.agent.model.AgentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduledPromptServiceTest {

    @TempDir
    Path tmp;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ZoneId zone = ZoneId.of("America/Chicago");

    private JsonFileScheduledPromptStore store;
    private ThreadPoolTaskScheduler scheduler;
    private AgentService agent;
    private CapturingDelivery delivery;
    private SchedulerProperties properties;

    static class CapturingDelivery implements ScheduledResultDelivery {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> target = new AtomicReference<>();
        final AtomicReference<String> result = new AtomicReference<>();

        @Override
        public void deliver(String target, String prompt, String result) {
            this.target.set(target);
            this.result.set(result);
            latch.countDown();
        }
    }

    @BeforeEach
    void setUp() {
        store = new JsonFileScheduledPromptStore(mapper, tmp.resolve("schedules.json").toString());
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        agent = mock(AgentService.class);
        when(agent.chat(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AgentResponse("the answer", List.of()));
        delivery = new CapturingDelivery();
        properties = new SchedulerProperties(true, tmp.resolve("schedules.json").toString(), 20, zone.getId());
    }

    private ScheduledPromptService newService(ScheduledResultDelivery d) {
        @SuppressWarnings("unchecked")
        ObjectProvider<AgentService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(agent);
        return new ScheduledPromptService(provider, scheduler, store, Optional.ofNullable(d), properties);
    }

    @Test
    void scheduleCronPersistsAndLists() {
        ScheduledPromptService service = newService(delivery);

        ScheduledPrompt sp = service.scheduleCron("do it", "0 9 * * 1-5", zone, "discord-dm-7", null);

        assertThat(sp.recurring()).isTrue();
        assertThat(service.listForTarget("discord-dm-7")).extracting(ScheduledPrompt::id).contains(sp.id());
        assertThat(store.findAll()).hasSize(1);
        assertThat(service.describeNextRun(sp)).isNotBlank();
    }

    @Test
    void oneShotFiresRunsAgentAndDelivers() throws Exception {
        ScheduledPromptService service = newService(delivery);

        service.scheduleOnce("summarise news", Instant.now().plusMillis(300), zone, "discord-channel-42", null);

        assertThat(delivery.latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(delivery.target.get()).isEqualTo("discord-channel-42");
        assertThat(delivery.result.get()).isEqualTo("the answer");
        // One-shot schedules remove themselves after firing (just after delivery, so poll briefly).
        long deadline = System.currentTimeMillis() + 2000;
        while (!store.findAll().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(store.findAll()).isEmpty();
    }

    @Test
    void cancelRemovesSchedule() {
        ScheduledPromptService service = newService(delivery);
        ScheduledPrompt sp = service.scheduleCron("x", "0 9 * * *", zone, "t", null);

        assertThat(service.cancel(sp.id())).isTrue();
        assertThat(service.listForTarget("t")).isEmpty();
        assertThat(store.findAll()).isEmpty();
    }

    @Test
    void reloadReArmsPersistedCron() {
        newService(delivery).scheduleCron("x", "0 9 * * *", zone, "t", null);

        // A fresh store + service over the same file simulates a restart.
        JsonFileScheduledPromptStore reopened =
                new JsonFileScheduledPromptStore(mapper, tmp.resolve("schedules.json").toString());
        assertThat(reopened.findAll()).hasSize(1);
    }

    @Test
    void rejectsPastOneShotAndInvalidCron() {
        ScheduledPromptService service = newService(delivery);

        assertThatThrownBy(() ->
                service.scheduleOnce("x", Instant.now().minusSeconds(60), zone, "t", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                service.scheduleCron("x", "not a cron", zone, "t", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesPerTargetQuota() {
        properties = new SchedulerProperties(true, tmp.resolve("schedules.json").toString(), 1, zone.getId());
        ScheduledPromptService service = newService(delivery);
        service.scheduleCron("x", "0 9 * * *", zone, "t", null);

        assertThatThrownBy(() -> service.scheduleCron("y", "0 10 * * *", zone, "t", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void normalizeCronPrependsSecondsToFiveFieldExpression() {
        assertThat(ScheduledPromptService.normalizeCron("0 9 * * 1-5")).isEqualTo("0 0 9 * * 1-5");
        assertThat(ScheduledPromptService.normalizeCron("0 0 9 * * 1-5")).isEqualTo("0 0 9 * * 1-5");
    }
}
