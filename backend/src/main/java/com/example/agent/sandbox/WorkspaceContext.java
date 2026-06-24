package com.example.agent.sandbox;

/**
 * Describes where a request's tools are allowed to operate.
 *
 * <p>Project chats run fully isolated: their repository lives only inside a Docker container, so
 * {@code hostAccessAllowed} is {@code false} and every filesystem/shell tool must route its work
 * through the {@link SandboxRuntime} for {@code sandboxId} ({@code repositoryPath} is the checkout's
 * path inside the container). Standard chats run against the host workspace, so
 * {@code hostAccessAllowed} is {@code true} and {@code sandboxId} is typically {@code null}.
 *
 * @param chatId            the originating chat/session id
 * @param sandboxId         the sandbox the request is bound to, or {@code null} for host chats
 * @param repositoryPath    the workspace root inside the container ({@code /workspace/repo}), or the
 *                          host workspace path for host chats
 * @param hostAccessAllowed whether tools may touch the host filesystem directly
 */
public record WorkspaceContext(
        String chatId,
        String sandboxId,
        String repositoryPath,
        boolean hostAccessAllowed) {

    /** A context for a standard host-backed chat (tools operate on the host workspace). */
    public static WorkspaceContext host(String chatId, String repositoryPath) {
        return new WorkspaceContext(chatId, null, repositoryPath, true);
    }

    /** A context for an isolated project chat (tools must route through the container). */
    public static WorkspaceContext container(String chatId, String sandboxId, String repositoryPath) {
        return new WorkspaceContext(chatId, sandboxId, repositoryPath, false);
    }

    /** Whether tools for this request must execute inside the sandbox container. */
    public boolean requiresContainer() {
        return !hostAccessAllowed && sandboxId != null && !sandboxId.isBlank();
    }
}
