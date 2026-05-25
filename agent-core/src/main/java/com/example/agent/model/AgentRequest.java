package com.example.agent.model;

/**
 * Request sent to the agent via the REST API.
 *
 * <p>{@code model} is optional; when blank the agent falls back to the configured default
 * ({@code llm.model}).
 *
 * <p>{@code sessionId} is optional; when present, short-term conversation memory recalls the recent
 * turns of that session and stores this exchange back. When blank, the request is stateless.
 */
public record AgentRequest(String prompt, String model, String sessionId) {

    /** Stateless request with no session memory. */
    public AgentRequest(String prompt, String model) {
        this(prompt, model, null);
    }
}
