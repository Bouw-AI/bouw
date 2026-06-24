package com.example.agent.sandbox;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * SPI for managing and executing work inside a per-chat sandbox (a Docker container).
 *
 * <p>Defined in {@code agent-core} so the agent loop and the built-in workspace tools stay decoupled
 * from any concrete container technology. The Docker-backed implementations live in the integration
 * module. The interface has two layers:
 *
 * <ul>
 *   <li>The low-level execution contract — {@link #isActive(String)} and
 *       {@link #exec(String, String, Duration)} — used by {@code run_bash}, the git tools, and the
 *       container-backed file tools to run commands inside a sandbox.</li>
 *   <li>The lifecycle/file contract — {@link #create}, {@link #readFile}, {@link #writeFile},
 *       {@link #listFiles}, {@link #restart}, {@link #delete}, {@link #inspect} — used by the
 *       sandbox-session service to provision and manage an isolated project workspace.</li>
 * </ul>
 *
 * <p>Implementations that only provide command execution (e.g. the legacy host-fallback path) leave
 * the lifecycle/file methods at their default {@code UnsupportedOperationException}.
 *
 * <p>Sandbox ids are threaded through the agent as {@code String}s (the {@link SandboxSession#id()}
 * rendered with {@link java.util.UUID#toString()}), so the lifecycle methods use {@code String} ids
 * for consistency with {@link #exec} and the request/tool plumbing.
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

    /**
     * Creates a fresh isolated sandbox for {@code chatSessionId}: a Docker volume + container, with
     * {@code repository} cloned inside the container. Returns the persisted session handle.
     */
    default SandboxSession create(String chatSessionId, RepositoryConfig repository) {
        throw new UnsupportedOperationException("This SandboxRuntime does not support create()");
    }

    /** Reads a UTF-8 file from inside the sandbox (path relative to the repository workspace). */
    default FileResult readFile(String sandboxId, String path) {
        throw new UnsupportedOperationException("This SandboxRuntime does not support readFile()");
    }

    /** Writes a UTF-8 file inside the sandbox, creating parent directories as needed. */
    default void writeFile(String sandboxId, String path, String content) {
        throw new UnsupportedOperationException("This SandboxRuntime does not support writeFile()");
    }

    /** Lists the entries of a directory inside the sandbox (path relative to the repository workspace). */
    default List<FileEntry> listFiles(String sandboxId, String path) {
        throw new UnsupportedOperationException("This SandboxRuntime does not support listFiles()");
    }

    /** Restarts a stopped sandbox container ({@code docker start}). */
    default void restart(String sandboxId) {
        throw new UnsupportedOperationException("This SandboxRuntime does not support restart()");
    }

    /** Stops and removes the sandbox's container and volume. */
    default void delete(String sandboxId) {
        throw new UnsupportedOperationException("This SandboxRuntime does not support delete()");
    }

    /** Returns the live status of the sandbox container, or {@code null} when it no longer exists. */
    default SandboxState inspect(String sandboxId) {
        throw new UnsupportedOperationException("This SandboxRuntime does not support inspect()");
    }

    /** Combined output and exit status of a command run inside a sandbox. */
    record ExecResult(int exitCode, String output, boolean timedOut) {}

    /** Live container status returned by {@link #inspect(String)}. */
    record SandboxState(String sandboxId, String containerId, SandboxStatus status, boolean running) {}
}
