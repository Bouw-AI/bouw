package com.example.agent.scheduler;

import java.time.Instant;

/**
 * A prompt the agent scheduled for itself to run later, on behalf of a caller.
 *
 * <p>Exactly one of {@code cron} (recurring) or {@code atEpochMillis} (one-shot) is set:
 * <ul>
 *   <li>{@code cron} — a Spring 6-field cron expression evaluated in {@code zone}; the prompt runs
 *       on every match until cancelled.</li>
 *   <li>{@code atEpochMillis} — a single absolute instant; the prompt runs once and is then removed.</li>
 * </ul>
 *
 * <p>{@code deliveryTarget} is the origin session id captured when the prompt was scheduled
 * (e.g. {@code discord-channel-123}). The scheduled run replays under that same session so it keeps
 * conversation continuity, and the result is delivered back to that target.
 */
public record ScheduledPrompt(
        String id,
        String prompt,
        String cron,
        Long atEpochMillis,
        String zone,
        String deliveryTarget,
        String model,
        long createdAtEpochMillis) {

    /** True when this is a recurring (cron) schedule rather than a one-shot. */
    public boolean recurring() {
        return cron != null && !cron.isBlank();
    }

    /** The one-shot fire instant, or {@code null} for recurring schedules. */
    public Instant at() {
        return atEpochMillis == null ? null : Instant.ofEpochMilli(atEpochMillis);
    }
}
