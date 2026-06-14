package com.example.agent.sandbox;

import java.io.IOException;
import java.time.Duration;

/**
 * SPI for executing shell commands inside a per-session sandbox (e.g. a Docker container).
 *
 * <p>Defined in {@code agent-core} so the agent loop and the built-in {@code run_bash} tool stay
 * decoupled from any concrete container/runtime technology. The Docker-backed implementation lives
 * in the integration module. When no implementation is present, {@code run_bash} falls back to
 * running commands directly on the host inside the workspace root.
 */
public interface SandboxRuntime {

    /** Whether a sandbox with this id currently exists and is able to execute commands. */
    boolean isActive(String sandboxId);

    /**
     * Executes {@code command} inside the sandbox identified by {@code sandboxId}, returning its
     * combined stdout/stderr and exit code.
     *
     * @param timeout wall-clock limit for the command
     */
    ExecResult exec(String sandboxId, String command, Duration timeout) throws IOException, InterruptedException;

    /** Combined output and exit status of a command run inside a sandbox. */
    record ExecResult(int exitCode, String output, boolean timedOut) {}
}
