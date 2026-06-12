package com.example.agent.scheduler;

import java.util.List;

/**
 * Persistence SPI for {@link ScheduledPrompt}s. Implemented in {@code agent-core} by the
 * file-backed {@link JsonFileScheduledPromptStore} (the default), but kept as an interface so the
 * storage backend can be swapped (e.g. Redis) without touching the scheduling logic.
 *
 * <p>Implementations must be safe for concurrent use — schedules can be added or removed from
 * different threads (the agent loop runs on a pool, triggers fire on the scheduler pool).
 */
public interface ScheduledPromptStore {

    /** Persists (or replaces by id) a scheduled prompt. */
    void save(ScheduledPrompt prompt);

    /**
     * Removes the scheduled prompt with the given id.
     *
     * @return true when a prompt with that id existed (and was removed)
     */
    boolean delete(String id);

    /** Returns all persisted scheduled prompts. */
    List<ScheduledPrompt> findAll();

    /**
     * Returns all schedules registered for the given delivery target. Implementations should
     * override this when they can answer it without scanning every schedule.
     */
    default List<ScheduledPrompt> findByTarget(String target) {
        if (target == null) {
            return List.of();
        }
        return findAll().stream()
                .filter(p -> target.equals(p.deliveryTarget()))
                .toList();
    }
}
