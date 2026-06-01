package com.example.agent.tool;

import com.example.agent.AgentService;
import com.example.agent.model.AgentResponse;
import com.example.agent.scheduler.JsonFileScheduledPromptStore;
import com.example.agent.scheduler.ScheduledPromptService;
import com.example.agent.scheduler.SchedulerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchedulePromptToolTest {

    @TempDir
    Path tmp;

    private ScheduledPromptService service;
    private SchedulePromptTool tool;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        var store = new JsonFileScheduledPromptStore(mapper, tmp.resolve("s.json").toString());
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        AgentService agent = mock(AgentService.class);
        when(agent.chat(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AgentResponse("answer", List.of()));
        @SuppressWarnings("unchecked")
        ObjectProvider<AgentService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(agent);
        var props = new SchedulerProperties(true, tmp.resolve("s.json").toString(), 20, "America/Chicago");
        service = new ScheduledPromptService(provider, scheduler, store, Optional.empty(), props);
        tool = new SchedulePromptTool(service);
    }

    private ToolContext ctx(String sessionId) {
        return new ToolContext(null, sessionId);
    }

    @Test
    void requiresExactlyOneOfCronOrAt() {
        assertThat(tool.execute(Map.of("prompt", "p"), ctx("t")))
                .contains("exactly one");
        assertThat(tool.execute(Map.of("prompt", "p", "cron", "0 9 * * *", "at", "2099-01-01T09:00"), ctx("t")))
                .contains("exactly one");
    }

    @Test
    void schedulesCronAndCapturesOriginAsDeliveryTarget() {
        String out = tool.execute(
                Map.of("prompt", "summarise reddit stock news",
                        "cron", "0 9 * * 1-5",
                        "timezone", "America/Chicago"),
                ctx("discord-channel-99"));

        assertThat(out).contains("Scheduled").contains("recurring").contains("delivered back");
        assertThat(service.listForTarget("discord-channel-99")).hasSize(1);
    }

    @Test
    void warnsWhenNoOriginSession() {
        String out = tool.execute(
                Map.of("prompt", "p", "cron", "0 9 * * *"), ctx(null));
        assertThat(out).contains("logged on the server");
    }

    @Test
    void rejectsUnknownTimezone() {
        assertThat(tool.execute(
                Map.of("prompt", "p", "cron", "0 9 * * *", "timezone", "Mars/Phobos"), ctx("t")))
                .contains("Unknown timezone");
    }

    @Test
    void listAndCancelToolsScopeToOrigin() {
        tool.execute(Map.of("prompt", "p", "cron", "0 9 * * *"), ctx("discord-dm-5"));
        String id = service.listForTarget("discord-dm-5").get(0).id();

        var list = new ListScheduledPromptsTool(service);
        assertThat(list.execute(Map.of(), ctx("discord-dm-5"))).contains(id);
        // A different origin cannot see or cancel it.
        var cancel = new CancelScheduledPromptTool(service);
        assertThat(cancel.execute(Map.of("id", id), ctx("discord-dm-OTHER")))
                .contains("belongs to this conversation").doesNotContain("Cancelled");
        assertThat(cancel.execute(Map.of("id", id), ctx("discord-dm-5")))
                .contains("Cancelled");
    }
}
