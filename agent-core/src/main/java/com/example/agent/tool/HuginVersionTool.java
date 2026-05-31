package com.example.agent.tool;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Retrieves the version of the hugin CLI. */
@Component
public class HuginVersionTool implements LocalTool {

    private static final String COMMON_PATH_PREFIX = "/opt/homebrew/bin:/usr/local/bin:/opt/local/bin";

    private final Duration timeout;
    private final int maxChars;

    public HuginVersionTool(LocalToolProperties properties) {
        this.timeout = properties.bashTimeout();
        this.maxChars = properties.maxOutputChars();
    }

    @Override
    public String name() {
        return "hugin_version";
    }

    @Override
    public String description() {
        return "Get the installed version of the hugin CLI tool.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of());
    }

    @Override
    public String execute(Map<String, Object> arguments) throws IOException, InterruptedException {
        List<List<String>> commands = List.of(
                List.of("hugin", "--version"),
                List.of("hugin", "version"),
                List.of("npm", "list", "-g", "hugin-agent", "--depth=0"));

        List<String> failures = new ArrayList<>();
        for (List<String> command : commands) {
            CommandResult result = run(command);
            String version = extractVersion(result.output());
            if (result.exitCode() == 0 && version != null) {
                return version;
            }
            failures.add(String.join(" ", command) + " exited with code " + result.exitCode()
                    + (result.output().isBlank() ? "" : "\n" + result.output().strip()));
        }

        return "Error: could not determine hugin version.\n" + String.join("\n\n", failures);
    }

    private CommandResult run(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        String currentPath = System.getenv("PATH") != null ? System.getenv("PATH") : "";
        builder.environment().put("PATH", COMMON_PATH_PREFIX + ":" + currentPath);
        builder.redirectErrorStream(true);

        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> drain(process, output));
        reader.setDaemon(true);
        reader.start();

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            reader.join(1000);
            return new CommandResult(124, String.join(" ", command)
                    + " timed out after " + timeout.toSeconds() + "s.");
        }
        reader.join(2000);

        return new CommandResult(process.exitValue(), render(output));
    }

    private void drain(Process process, StringBuilder output) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            int ch;
            while ((ch = in.read()) != -1) {
                if (output.length() < maxChars) {
                    output.append((char) ch);
                }
            }
        } catch (IOException ignored) {
            // process output stream closed/interrupted — keep what we have
        }
    }

    private String render(StringBuilder output) {
        if (output.length() >= maxChars) {
            return output + "\n... [output truncated at " + maxChars + " characters]";
        }
        return output.toString();
    }

    static String extractVersion(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        String text = output.strip();
        if (text.startsWith("Usage: hugin")) {
            return null;
        }

        for (String line : text.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.matches("v?\\d+\\.\\d+\\.\\d+.*")) {
                return trimmed.startsWith("v") ? trimmed.substring(1) : trimmed;
            }
            int packageAt = trimmed.indexOf("hugin-agent@");
            if (packageAt >= 0) {
                String candidate = trimmed.substring(packageAt + "hugin-agent@".length()).strip();
                int end = 0;
                while (end < candidate.length()
                        && !Character.isWhitespace(candidate.charAt(end))
                        && candidate.charAt(end) != ')') {
                    end++;
                }
                if (end > 0) {
                    return candidate.substring(0, end);
                }
            }
        }
        return null;
    }

    private record CommandResult(int exitCode, String output) {}
}
