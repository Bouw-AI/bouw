package com.example.agent.tool;

import com.example.agent.sandbox.SandboxRuntime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Creates a new file or overwrites an existing one within the workspace. */
@Component
public class WriteFileTool implements LocalTool {

    private final Workspace workspace;
    private final PathDenyList denyList;
    private final Optional<SandboxRuntime> sandboxRuntime;

    @Autowired
    public WriteFileTool(Workspace workspace, PathDenyList denyList, Optional<SandboxRuntime> sandboxRuntime) {
        this.workspace = workspace;
        this.denyList = denyList;
        this.sandboxRuntime = sandboxRuntime;
    }

    /** Convenience constructor for tests / hosts without a sandbox runtime (host execution only). */
    public WriteFileTool(Workspace workspace, PathDenyList denyList) {
        this(workspace, denyList, Optional.empty());
    }

    @Override
    public boolean requiresWorkspace() {
        return true;
    }

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String description() {
        return "Create a new file or overwrite an existing file with the given content. "
                + "Parent directories are created as needed. Use edit_file for targeted "
                + "changes to large files.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "File path, relative to the workspace root."),
                        "content", Map.of(
                                "type", "string",
                                "description", "Full content to write to the file.")),
                "required", List.of("path", "content"));
    }

    @Override
    public String execute(Map<String, Object> arguments) throws IOException {
        return execute(arguments, new ToolContext(workspace));
    }

    @Override
    public String execute(Map<String, Object> arguments, ToolContext ctx) throws IOException {
        String requested = requiredString(arguments, "path");
        String content = presentString(arguments, "content");

        if (denyList.isDenied(requested)) {
            return "Error: access to '" + requested + "' is denied by configuration.";
        }

        // Isolated project chat: write the file inside the sandbox container, never the host.
        if (ctx.requiresContainer() && sandboxRuntime.isPresent()) {
            return ContainerWorkspaceTools.writeFile(sandboxRuntime.get(),
                    ctx.workspaceContext().sandboxId(), requested, content);
        }

        Workspace ws = ctx.workspace();
        Path file = ws.resolve(requested);
        String relative = ws.relativize(file);

        if (denyList.isDenied(relative)) {
            return "Error: access to '" + requested + "' is denied by configuration.";
        }

        if (Files.isDirectory(file)) {
            return "Error: path is a directory, not a file: " + requested;
        }

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, content);
        return "Wrote " + content.length() + " characters to " + relative;
    }
}
