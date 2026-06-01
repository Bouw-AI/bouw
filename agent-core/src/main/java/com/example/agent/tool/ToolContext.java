package com.example.agent.tool;

/**
 * Carries per-request context passed to {@link LocalTool#execute(java.util.Map, ToolContext)}.
 * The workspace may be different for each cloud agent, so it is resolved at call time and
 * threaded through here rather than being held as a singleton by the tool.
 *
 * <p>{@code sessionId} identifies the origin of the request (e.g. {@code discord-channel-123}).
 * Tools that need to route something back to the caller — such as {@code schedule_prompt}
 * delivering a scheduled result — use it as the reply-to / delivery target. It may be {@code null}
 * for stateless requests.
 */
public record ToolContext(Workspace workspace, String sessionId) {

    /** Context with no session origin (stateless request). */
    public ToolContext(Workspace workspace) {
        this(workspace, null);
    }
}
