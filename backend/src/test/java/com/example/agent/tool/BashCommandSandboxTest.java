package com.example.agent.tool;

import com.example.agent.sandbox.SandboxRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link BashCommandTool} routes commands to the {@link SandboxRuntime} when the
 * request is bound to an active sandbox, and otherwise falls back to host execution.
 */
class BashCommandSandboxTest {

    @TempDir
    Path tmp;

    private Workspace workspace;
    private LocalToolProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LocalToolProperties(true, tmp.toString(), Duration.ofSeconds(10), 30_000, List.of());
        workspace = new Workspace(properties);
    }

    @Test
    void routesCommandIntoActiveSandbox() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        SandboxRuntime runtime = new SandboxRuntime() {
            @Override
            public boolean isActive(String sandboxId) {
                return "sbx-1".equals(sandboxId);
            }

            @Override
            public ExecResult exec(String sandboxId, String command, Duration timeout) {
                seen.set(sandboxId + ":" + command);
                return new ExecResult(0, "from-container", false);
            }
        };
        var bash = new BashCommandTool(workspace, properties, Optional.of(runtime));

        String result = bash.execute(Map.of("command", "echo hi"),
                new ToolContext(workspace, "session", null, null, null, "sbx-1"));

        assertThat(seen.get()).isEqualTo("sbx-1:echo hi");
        assertThat(result).contains("exit code: 0").contains("from-container");
    }

    @Test
    void surfacesSandboxTimeout() throws Exception {
        SandboxRuntime runtime = new SandboxRuntime() {
            @Override
            public boolean isActive(String sandboxId) {
                return true;
            }

            @Override
            public ExecResult exec(String sandboxId, String command, Duration timeout) {
                return new ExecResult(-1, "partial", true);
            }
        };
        var bash = new BashCommandTool(workspace, properties, Optional.of(runtime));

        String result = bash.execute(Map.of("command", "sleep 999"),
                new ToolContext(workspace, "session", null, null, null, "sbx-1"));

        assertThat(result).contains("timed out").contains("partial");
    }

    @Test
    void fallsBackToHostWhenNoSandboxOnContext() throws Exception {
        SandboxRuntime runtime = new SandboxRuntime() {
            @Override
            public boolean isActive(String sandboxId) {
                return true;
            }

            @Override
            public ExecResult exec(String sandboxId, String command, Duration timeout) {
                throw new AssertionError("should not exec in sandbox without a sandboxId on the context");
            }
        };
        var bash = new BashCommandTool(workspace, properties, Optional.of(runtime));

        // No sandboxId on the context -> runs on the host shell.
        String result = bash.execute(Map.of("command", "echo host-side"),
                new ToolContext(workspace, "session"));

        assertThat(result).contains("exit code: 0").contains("host-side");
    }

    @Test
    void fallsBackToHostWhenSandboxInactive() throws Exception {
        SandboxRuntime runtime = new SandboxRuntime() {
            @Override
            public boolean isActive(String sandboxId) {
                return false;
            }

            @Override
            public ExecResult exec(String sandboxId, String command, Duration timeout) {
                throw new AssertionError("should not exec in an inactive sandbox");
            }
        };
        var bash = new BashCommandTool(workspace, properties, Optional.of(runtime));

        String result = bash.execute(Map.of("command", "echo host-side"),
                new ToolContext(workspace, "session", null, null, null, "missing"));

        assertThat(result).contains("exit code: 0").contains("host-side");
    }
}
