package com.example.terminal;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates the events of a terminal session — user prompts, assistant responses (capped at
 * 100 words), tool calls and tool results — so they can be included in a bug report.
 */
public class ConversationLog {

    sealed interface Entry permits UserEntry, AssistantEntry, ToolCallEntry, ToolResultEntry {}
    record UserEntry(String prompt) implements Entry {}
    record AssistantEntry(String text) implements Entry {}
    record ToolCallEntry(String name, String args) implements Entry {}
    record ToolResultEntry(String name, String result) implements Entry {}

    private final List<Entry> entries = new ArrayList<>();
    private final StringBuilder tokenBuffer = new StringBuilder();

    public void recordUserPrompt(String prompt) {
        entries.add(new UserEntry(prompt));
    }

    public void appendToken(String text) {
        tokenBuffer.append(text);
    }

    /** Finalises the buffered assistant tokens as an entry (called after each streamed response). */
    public void flushAssistantMessage() {
        String text = tokenBuffer.toString().strip();
        tokenBuffer.setLength(0);
        if (!text.isBlank()) {
            entries.add(new AssistantEntry(truncateToWords(text, 100)));
        }
    }

    public void recordToolCall(String name, String args) {
        entries.add(new ToolCallEntry(name, args));
    }

    public void recordToolResult(String name, String result) {
        entries.add(new ToolResultEntry(name, result));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void clear() {
        entries.clear();
        tokenBuffer.setLength(0);
    }

    /** Returns a Markdown-formatted summary of the conversation for use in a GitHub issue body. */
    public String formatMarkdown() {
        StringBuilder sb = new StringBuilder();
        int turn = 0;
        for (Entry entry : entries) {
            switch (entry) {
                case UserEntry u -> {
                    turn++;
                    sb.append("### Turn ").append(turn).append("\n\n");
                    sb.append("**User:** ").append(u.prompt()).append("\n\n");
                }
                case AssistantEntry a -> sb.append("**Assistant:** ").append(a.text()).append("\n\n");
                case ToolCallEntry tc -> sb.append("**Tool call:** `").append(tc.name()).append("` — ")
                        .append(compact(tc.args(), 300)).append("\n\n");
                case ToolResultEntry tr -> sb.append("**Tool result (`").append(tr.name()).append("`):** ")
                        .append(compact(tr.result(), 400)).append("\n\n");
            }
        }
        return sb.toString();
    }

    private static String truncateToWords(String text, int maxWords) {
        String[] words = text.split("\\s+");
        if (words.length <= maxWords) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            if (i > 0) sb.append(' ');
            sb.append(words[i]);
        }
        return sb.append(" …").toString();
    }

    private static String compact(String s, int maxChars) {
        if (s == null) return "";
        String c = s.replaceAll("\\s+", " ").strip();
        return c.length() > maxChars ? c.substring(0, maxChars - 1) + "…" : c;
    }
}
