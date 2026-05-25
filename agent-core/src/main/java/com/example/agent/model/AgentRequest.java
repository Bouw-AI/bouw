package com.example.agent.model;

/**
 * Request sent to the agent via the REST API.
 *
 * <p>{@code model} is optional; when blank the agent falls back to the configured default
 * ({@code llm.model}).
 */
public record AgentRequest(String prompt, String model) {}
