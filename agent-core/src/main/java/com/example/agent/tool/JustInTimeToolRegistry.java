package com.example.agent.tool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Loads just-in-time tools from the current workspace.
 *
 * <p>Tool manifests live under {@code .hugin/jit-tools/*.json} in each workspace. The agent
 * rescans that directory on every loop iteration, so once Hugin writes a manifest it becomes
 * available on the next reasoning pass without restarting the service.
 */
@Component
public class JustInTimeToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(JustInTimeToolRegistry.class);

    private final LocalToolProperties properties;
    private final ObjectMapper objectMapper;

    public JustInTimeToolRegistry(LocalToolProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<LocalTool> tools(Workspace workspace) {
        if (!Boolean.TRUE.equals(properties.enabled())) {
            return List.of();
        }

        Path manifestDir = manifestDirectory(workspace);
        if (!Files.isDirectory(manifestDir)) {
            return List.of();
        }

        Map<String, LocalTool> byName = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(manifestDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> loadManifest(workspace, path)
                            .ifPresent(tool -> {
                                LocalTool previous = byName.put(tool.name(), tool);
                                if (previous != null) {
                                    log.warn("Duplicate JIT tool name '{}' in {}; keeping first definition",
                                            tool.name(), manifestDir);
                                }
                            }));
        } catch (IOException e) {
            log.warn("Could not scan JIT tool directory {}: {}", manifestDir, e.getMessage());
            return List.of();
        }

        return List.copyOf(byName.values());
    }

    public LocalTool find(String name, Workspace workspace) {
        for (LocalTool tool : tools(workspace)) {
            if (tool.name().equals(name)) {
                return tool;
            }
        }
        return null;
    }

    private java.util.Optional<LocalTool> loadManifest(Workspace workspace, Path path) {
        try {
            JitToolManifest manifest = objectMapper.readValue(path.toFile(), JitToolManifest.class);
            if (manifest.name() == null || manifest.name().isBlank()) {
                log.warn("Skipping JIT tool manifest {}: missing 'name'", path);
                return java.util.Optional.empty();
            }
            if (manifest.command() == null || manifest.command().isBlank()) {
                log.warn("Skipping JIT tool manifest {}: missing 'command'", path);
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new ManifestLocalTool(workspace, path, manifest));
        } catch (Exception e) {
            log.warn("Skipping invalid JIT tool manifest {}: {}", path, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private Path manifestDirectory(Workspace workspace) {
        return workspace.resolve(properties.jitToolDirectory());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JitToolManifest(
            String name,
            String description,
            Map<String, Object> inputSchema,
            String command,
            List<String> args,
            Map<String, String> env,
            String workingDirectory,
            Integer timeoutSeconds,
            Boolean passArgumentsViaStdin) {
        public JitToolManifest {
            if (inputSchema == null) {
                inputSchema = Map.of(
                        "type", "object",
                        "properties", Map.of());
            }
            if (args == null) {
                args = List.of();
            }
            if (env == null) {
                env = Map.of();
            }
            if (passArgumentsViaStdin == null) {
                passArgumentsViaStdin = true;
            }
        }
    }

    private final class ManifestLocalTool implements LocalTool {
        private final Workspace workspace;
        private final Path manifestPath;
        private final JitToolManifest manifest;

        private ManifestLocalTool(Workspace workspace, Path manifestPath, JitToolManifest manifest) {
            this.workspace = workspace;
            this.manifestPath = manifestPath;
            this.manifest = manifest;
        }

        @Override
        public String name() {
            return manifest.name();
        }

        @Override
        public String description() {
            String description = manifest.description();
            return description == null || description.isBlank()
                    ? "Workspace-local just-in-time tool loaded from " + workspace.relativize(manifestPath)
                    : description;
        }

        @Override
        public Map<String, Object> inputSchema() {
            return manifest.inputSchema();
        }

        @Override
        public String execute(Map<String, Object> arguments) throws Exception {
            return execute(arguments, new ToolContext(workspace));
        }

        @Override
        public String execute(Map<String, Object> arguments, ToolContext ctx) throws Exception {
            Workspace ws = ctx.workspace();
            Path workingDir = resolveWorkingDirectory(ws);
            Duration timeout = timeout();

            List<String> commandLine = new ArrayList<>();
            commandLine.add(manifest.command());
            commandLine.addAll(manifest.args());

            ProcessBuilder builder = new ProcessBuilder(commandLine);
            builder.directory(workingDir.toFile());
            builder.redirectErrorStream(true);
            builder.environment().putAll(manifest.env());
            builder.environment().put("HUGIN_TOOL_ARGS_JSON", objectMapper.writeValueAsString(arguments));

            Process process = builder.start();
            if (manifest.passArgumentsViaStdin()) {
                try (var writer = new java.io.OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                    writer.write(objectMapper.writeValueAsString(arguments));
                    writer.flush();
                }
            } else {
                process.getOutputStream().close();
            }

            StringBuilder output = new StringBuilder();
            Thread reader = new Thread(() -> drain(process, output));
            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.join(1000);
                return "Error: JIT tool timed out after " + timeout.toSeconds() + "s.\n"
                        + "Partial output:\n" + render(output);
            }
            reader.join(2000);

            int exitCode = process.exitValue();
            String rendered = render(output);
            return "exit code: " + exitCode + (rendered.isBlank() ? " (no output)" : "\n" + rendered);
        }

        private Path resolveWorkingDirectory(Workspace ws) {
            if (manifest.workingDirectory() == null || manifest.workingDirectory().isBlank()) {
                return ws.root();
            }
            return ws.resolve(manifest.workingDirectory());
        }

        private Duration timeout() {
            int configured = manifest.timeoutSeconds() != null && manifest.timeoutSeconds() > 0
                    ? manifest.timeoutSeconds()
                    : Math.toIntExact(properties.bashTimeout().toSeconds());
            return Duration.ofSeconds(configured);
        }

        private void drain(Process process, StringBuilder output) {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                int ch;
                while ((ch = in.read()) != -1) {
                    if (output.length() < properties.maxOutputChars()) {
                        output.append((char) ch);
                    }
                }
            } catch (IOException ignored) {
                // process output stream closed/interrupted — keep what we have
            }
        }

        private String render(StringBuilder output) {
            if (output.length() >= properties.maxOutputChars()) {
                return output + "\n... [output truncated at " + properties.maxOutputChars() + " characters]";
            }
            return output.toString();
        }
    }
}
