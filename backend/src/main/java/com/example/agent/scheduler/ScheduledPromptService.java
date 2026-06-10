package com.example.agent.scheduler;

import com.example.agent.AgentService;
import com.example.agent.model.AgentRequest;
import com.example.agent.model.AgentResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Owns the lifecycle of self-scheduled prompts: registering triggers, running the prompt through the
 * agent when a trigger fires, delivering the result back to the origin, and persisting/reloading the
 * set across restarts.
 *
 * <p>Depends on {@link AgentService} lazily (via {@link ObjectProvider}) to break the dependency
 * cycle — the {@code schedule_prompt} tool is itself part of the agent's tool set, so resolving the
 * agent only when a trigger fires keeps bean construction acyclic.
 */
@Service
@ConditionalOnProperty(name = "agent.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledPromptService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPromptService.class);
    private static final DateTimeFormatter HUMAN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private final ObjectProvider<AgentService> agentServiceProvider;
    private final TaskScheduler scheduler;
    private final ScheduledPromptStore store;
    private final Optional<ScheduledResultDelivery> delivery;
    private final SchedulerProperties properties;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> active = new ConcurrentHashMap<>();

    public ScheduledPromptService(ObjectProvider<AgentService> agentServiceProvider,
                                  TaskScheduler scheduledPromptScheduler,
                                  ScheduledPromptStore store,
                                  Optional<ScheduledResultDelivery> delivery,
                                  SchedulerProperties properties) {
        this.agentServiceProvider = agentServiceProvider;
        this.scheduler = scheduledPromptScheduler;
        this.store = store;
        this.delivery = delivery;
        this.properties = properties;
    }

    /** Re-arms every persisted schedule on startup. */
    @PostConstruct
    void reload() {
        List<ScheduledPrompt> persisted = store.findAll();
        int armed = 0;
        for (ScheduledPrompt prompt : persisted) {
            try {
                arm(prompt);
                armed++;
            } catch (Exception e) {
                log.warn("Could not re-arm scheduled prompt {}: {}", prompt.id(), e.getMessage());
            }
        }
        if (armed > 0) {
            log.info("Re-armed {} scheduled prompt(s)", armed);
        }
        if (delivery.isEmpty()) {
            log.info("No ScheduledResultDelivery bean present — scheduled results will be logged only");
        }
    }

    @PreDestroy
    void shutdown() {
        active.values().forEach(f -> f.cancel(false));
        active.clear();
    }

    public SchedulerProperties properties() {
        return properties;
    }

    /**
     * Creates, persists, and arms a recurring (cron) schedule.
     *
     * @return the stored prompt (with its generated id)
     */
    public ScheduledPrompt scheduleCron(String prompt, String cron, ZoneId zone,
                                        String deliveryTarget, String model) {
        String normalized = normalizeCron(cron);
        if (!CronExpression.isValidExpression(normalized)) {
            throw new IllegalArgumentException("Invalid cron expression: '" + cron + "'");
        }
        enforceQuota(deliveryTarget);
        ScheduledPrompt sp = new ScheduledPrompt(
                newId(), prompt, normalized, null, zone.getId(), deliveryTarget, model,
                System.currentTimeMillis());
        store.save(sp);
        arm(sp);
        return sp;
    }

    /**
     * Creates, persists, and arms a one-shot schedule that fires once at {@code at}.
     *
     * @return the stored prompt (with its generated id)
     */
    public ScheduledPrompt scheduleOnce(String prompt, Instant at, ZoneId zone,
                                        String deliveryTarget, String model) {
        if (at.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Scheduled time " + format(at, zone) + " is in the past");
        }
        enforceQuota(deliveryTarget);
        ScheduledPrompt sp = new ScheduledPrompt(
                newId(), prompt, null, at.toEpochMilli(), zone.getId(), deliveryTarget, model,
                System.currentTimeMillis());
        store.save(sp);
        arm(sp);
        return sp;
    }

    /** Schedules a prompt with the trigger appropriate to its type. */
    private void arm(ScheduledPrompt sp) {
        if (sp.recurring()) {
            CronTrigger trigger = new CronTrigger(sp.cron(), ZoneId.of(sp.zone()));
            ScheduledFuture<?> future = scheduler.schedule(() -> run(sp), trigger);
            if (future != null) {
                active.put(sp.id(), future);
            }
        } else {
            Instant at = sp.at();
            if (at.isBefore(Instant.now())) {
                // Missed while the server was down — run it once now, then it removes itself.
                log.info("Scheduled prompt {} was due at {} while offline; running now",
                        sp.id(), format(at, ZoneId.of(sp.zone())));
                scheduler.schedule(() -> run(sp), Instant.now().plusSeconds(5));
            } else {
                ScheduledFuture<?> future = scheduler.schedule(() -> run(sp), at);
                if (future != null) {
                    active.put(sp.id(), future);
                }
            }
        }
    }

    /** Executes a scheduled prompt: run it through the agent, deliver the result. */
    private void run(ScheduledPrompt sp) {
        log.info("Running scheduled prompt {} (target={})", sp.id(), sp.deliveryTarget());
        String result;
        try {
            AgentService agent = agentServiceProvider.getObject();
            AgentResponse response = agent.chat(
                    new AgentRequest(sp.prompt(), sp.model(), sp.deliveryTarget()));
            result = response.response();
        } catch (Exception e) {
            log.error("Scheduled prompt {} failed", sp.id(), e);
            result = "Scheduled task failed: " + e.getMessage();
        }

        final String delivered = result;
        try {
            delivery.ifPresentOrElse(
                    d -> d.deliver(sp.deliveryTarget(), sp.prompt(), delivered),
                    () -> log.info("Scheduled prompt {} result (no delivery channel):\n{}",
                            sp.id(), delivered));
        } catch (Exception e) {
            log.error("Failed to deliver scheduled prompt {}", sp.id(), e);
        }

        if (!sp.recurring()) {
            cancel(sp.id());
        }
    }

    /** Cancels and removes a schedule by id. @return true if it existed. */
    public boolean cancel(String id) {
        ScheduledFuture<?> future = active.remove(id);
        if (future != null) {
            future.cancel(false);
        }
        boolean existed = store.findAll().stream().anyMatch(p -> p.id().equals(id));
        store.delete(id);
        return existed || future != null;
    }

    /** All schedules currently registered for a given delivery target (origin session). */
    public List<ScheduledPrompt> listForTarget(String target) {
        return store.findAll().stream()
                .filter(p -> p.deliveryTarget() != null && p.deliveryTarget().equals(target))
                .toList();
    }

    /** A human-readable description of when {@code sp} will next run. */
    public String describeNextRun(ScheduledPrompt sp) {
        ZoneId zone = ZoneId.of(sp.zone());
        if (sp.recurring()) {
            ZonedDateTime next = CronExpression.parse(sp.cron()).next(ZonedDateTime.now(zone));
            return next == null ? "never (cron has no future match)" : next.format(HUMAN);
        }
        return format(sp.at(), zone);
    }

    private void enforceQuota(String target) {
        if (target == null) {
            return;
        }
        long count = listForTarget(target).size();
        if (count >= properties.maxPerTarget()) {
            throw new IllegalStateException(
                    "You already have " + count + " scheduled tasks (the maximum). "
                            + "Cancel one before scheduling another.");
        }
    }

    /** Spring cron is 6-field (sec min hour dom mon dow); accept a 5-field expression too. */
    static String normalizeCron(String cron) {
        if (cron == null) {
            return null;
        }
        String trimmed = cron.trim().replaceAll("\\s+", " ");
        if (trimmed.split(" ").length == 5) {
            return "0 " + trimmed;
        }
        return trimmed;
    }

    private static String format(Instant instant, ZoneId zone) {
        return instant.atZone(zone).format(HUMAN);
    }

    private static String newId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
