package com.example.agent.model;

import java.time.Instant;

/**
 * Metadata describing a per-session sandbox environment.
 *
 * <p>A sandbox is a container (Docker) created for a chat session. Its {@code id} is also the key
 * callers pass back on {@link AgentRequest#sandboxId()} so subsequent chat/stream requests run their
 * tools inside that environment. {@code workspace} is the host-side directory bind-mounted into the
 * container (and the agent's confined workspace root); {@code containerName} is the underlying
 * container name.
 */
public record SandboxInfo(
        String id,
        String containerName,
        String image,
        String status,
        Instant createdAt,
        String workspace) {

    /** Sandbox lifecycle states. */
    public static final String RUNNING = "running";
    public static final String STOPPED = "stopped";
    public static final String ERROR = "error";
}
