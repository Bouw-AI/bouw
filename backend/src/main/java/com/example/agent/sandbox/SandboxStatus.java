package com.example.agent.sandbox;

/**
 * Lifecycle states of an isolated {@link SandboxSession}.
 *
 * <pre>
 * CREATING  → the container/volume are being provisioned and the repository cloned
 * READY     → the container is running and able to execute the agent's tools
 * FAILED    → provisioning or a later operation failed; recoverable by recreating
 * STOPPED   → the container exists but is not running (can be restarted with docker start)
 * DESTROYED → the container, volume, and workspace have been removed
 * </pre>
 */
public enum SandboxStatus {
    CREATING,
    READY,
    FAILED,
    STOPPED,
    DESTROYED
}
