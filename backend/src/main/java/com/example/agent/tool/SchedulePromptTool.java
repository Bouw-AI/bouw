package com.example.agent.tool;

import com.example.agent.scheduler.ScheduledPrompt;
import com.example.agent.scheduler.ScheduledPromptService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Lets the agent schedule a prompt for itself to run later, delivering the result back to whoever
 * asked. The origin of the current request (its session id) is captured automatically as the
 * delivery target, so a scheduled run's answer is routed back to the same channel/DM/caller.
 */
@Component
@ConditionalOnProperty(name = "agent.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulePromptTool implements LocalTool {

    private static final DateTimeFormatter HUMAN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private final ScheduledPromptService service;

    public SchedulePromptTool(ScheduledPromptService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "schedule_prompt";
    }

    @Override
    public String description() {
        String zone = service.properties().defaultZone();
        String now = ZonedDateTime.now(ZoneId.of(zone)).format(HUMAN);
        return "Schedule a prompt for you (the agent) to run at a later time, on behalf of the user. "
                + "When the schedule fires, the prompt is run through the full agent (tools included) "
                + "and the result is delivered back to whoever asked — the same channel/DM/caller this "
                + "request came from. Use this whenever the user asks for something to happen at a "
                + "specific time or on a recurring basis (e.g. 'every weekday at 9am CST, summarise "
                + "Reddit stock news'). Provide EITHER 'cron' (recurring) OR 'at' (one-shot), not both. "
                + "Write 'prompt' as a self-contained instruction to your future self, since it runs "
                + "with no further input. Current server time is " + now + " (default timezone "
                + zone + "); convert the user's requested time into the right schedule and timezone.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "prompt", Map.of(
                                "type", "string",
                                "description", "The self-contained instruction to run at the scheduled "
                                        + "time (e.g. 'Search for the latest Reddit stock news and give "
                                        + "a concise summary')."),
                        "cron", Map.of(
                                "type", "string",
                                "description", "Recurring schedule as a cron expression. Standard "
                                        + "5-field (min hour day-of-month month day-of-week) or 6-field "
                                        + "(with leading seconds) is accepted. Example: '0 9 * * 1-5' = "
                                        + "weekdays at 9:00. Omit when using 'at'."),
                        "at", Map.of(
                                "type", "string",
                                "description", "One-shot run time as ISO-8601 local date-time, e.g. "
                                        + "'2026-06-02T09:00'. Interpreted in 'timezone'. Omit when "
                                        + "using 'cron'."),
                        "timezone", Map.of(
                                "type", "string",
                                "description", "IANA timezone for the schedule, e.g. 'America/Chicago' "
                                        + "for US Central. Defaults to the server's timezone.")),
                "required", List.of("prompt"));
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        return execute(arguments, new ToolContext(null));
    }

    @Override
    public String execute(Map<String, Object> arguments, ToolContext ctx) {
        String prompt = requiredString(arguments, "prompt");
        String cron = optionalString(arguments, "cron", "");
        String at = optionalString(arguments, "at", "");
        String tz = optionalString(arguments, "timezone", service.properties().defaultZone());

        if (cron.isBlank() == at.isBlank()) {
            return "Provide exactly one of 'cron' (recurring) or 'at' (one-shot).";
        }

        ZoneId zone;
        try {
            zone = ZoneId.of(tz);
        } catch (Exception e) {
            return "Unknown timezone '" + tz + "'. Use an IANA zone id like 'America/Chicago'.";
        }

        String target = ctx == null ? null : ctx.sessionId();

        try {
            ScheduledPrompt sp = cron.isBlank()
                    ? service.scheduleOnce(prompt, parseInstant(at, zone), zone, target, null)
                    : service.scheduleCron(prompt, cron, zone, target, null);

            StringBuilder sb = new StringBuilder();
            sb.append("Scheduled. id=").append(sp.id())
                    .append(sp.recurring() ? " (recurring)" : " (one-shot)")
                    .append(". Next run: ").append(service.describeNextRun(sp)).append(".");
            if (target == null || target.isBlank()) {
                sb.append(" Note: this request has no origin session, so the result will be logged "
                        + "on the server rather than sent to a chat.");
            } else {
                sb.append(" The result will be delivered back to this conversation.");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Could not schedule: " + e.getMessage();
        }
    }

    /** Parses an ISO local date-time (or zoned/offset) into an absolute instant in {@code zone}. */
    private static Instant parseInstant(String value, ZoneId zone) {
        String v = value.trim();
        try {
            return LocalDateTime.parse(v).atZone(zone).toInstant();
        } catch (Exception ignored) {
            // fall through to zone/offset-aware parsing
        }
        try {
            return OffsetDateTime.parse(v).toInstant();
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return Instant.parse(v);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Could not parse 'at' time '" + value + "'. Use ISO-8601 like '2026-06-02T09:00'.");
        }
    }
}
