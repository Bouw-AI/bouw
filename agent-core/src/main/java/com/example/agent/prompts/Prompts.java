package com.example.agent.prompts;

import com.example.agent.SystemFacts;

/**
 * Central home for every prompt string sent to the LLM.
 *
 * <p>Keep all tunable text here so prompt changes don't require hunting through
 * service classes.  Static constants are used for strings that never vary;
 * static methods are used where runtime data must be interpolated.
 */
public final class Prompts {

    private Prompts() {}

    // ── Tool-use system prompt ────────────────────────────────────────────────

    /**
     * Injected as a system message on every request that has at least one tool
     * available.  Instructs the model to prefer tools over guessing and to always
     * produce a conversational text reply after tool calls complete.
     */
    public static final String TOOL_USE = """
            You are a helpful assistant with access to external tools. When a tool can help \
            fulfil the user's request — for example reading, writing, or editing files, \
            searching the codebase, or running shell commands — call the relevant tool instead \
            of guessing or answering from memory. You may call tools several times in sequence, \
            using each result to decide the next step. After your tool calls complete, you MUST \
            always write a conversational text response to the user explaining what you found or \
            did — never end on a tool call alone. If no tool is relevant, simply answer normally.""";

    // ── Long-term memory injection ────────────────────────────────────────────

    /**
     * Header prepended to the block of recalled long-term memories that is
     * injected as a system message before the current user turn.
     */
    public static final String MEMORY_HEADER = """
            Relevant context recalled from long-term memory of past conversations. \
            Use it if it helps answer the user; ignore it if it is not relevant:""";

    // ── System-facts summary ──────────────────────────────────────────────────

    /**
     * Builds the compact system-facts message injected on every request so the
     * model knows the host machine's capabilities without needing to probe it
     * at chat time.
     */
    public static String systemFacts(SystemFacts f) {
        long totalMb = f.totalMemoryBytes() / (1024 * 1024);
        long freeGb  = f.freeDiskBytes()    / (1024 * 1024 * 1024);
        StringBuilder sb = new StringBuilder("System facts (this machine):\n");
        sb.append("OS: ").append(f.osName()).append(' ').append(f.osVersion())
          .append(" (").append(f.arch()).append(")\n");
        sb.append("CPU: ").append(f.availableProcessors()).append(" cores\n");
        sb.append("RAM: ").append(totalMb).append(" MB\n");
        sb.append("Disk free: ").append(freeGb).append(" GB\n");
        sb.append("Java: ").append(f.javaVersion()).append("\n");
        sb.append("Toolchains:");
        f.toolchains().forEach((tool, present) ->
                sb.append("\n  ").append(tool).append(": ").append(present ? "present" : "absent"));
        return sb.toString();
    }
}
