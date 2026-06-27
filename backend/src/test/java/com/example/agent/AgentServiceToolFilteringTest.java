package com.example.agent;

import com.example.agent.tool.JustInTimeToolRegistry;
import com.example.agent.tool.LocalTool;
import com.example.agent.tool.LocalToolProperties;
import com.example.agent.tool.LocalToolRegistry;
import com.example.agent.tool.Workspace;
import com.example.agent.tool.WorkspaceRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the dynamic tool set: filesystem/shell tools are only advertised for sandbox-bound
 * requests, and integration tools that report themselves unavailable are never advertised.
 */
class AgentServiceToolFilteringTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AgentService serviceWith(LocalTool... tools) {
        var props = new LocalToolProperties(true, ".", Duration.ofSeconds(30), 30_000, List.of());
        var localRegistry = new LocalToolRegistry(List.of(tools), props);
        var jitRegistry = new JustInTimeToolRegistry(props, objectMapper);
        var workspaceRegistry = new WorkspaceRegistry(new Workspace(props));
        return new AgentService(
                null,
                localRegistry,
                jitRegistry,
                objectMapper,
                Duration.ofMinutes(5),
                "model",
                10,
                workspaceRegistry,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    @Test
    void pureChatOmitsWorkspaceToolsButKeepsPlainTools() {
        var service = serviceWith(
                new StubTool("chat_tool", false, true),
                new StubTool("read_file", true, true));

        var names = service.availableTools(null).stream().map(AgentService.ToolSummary::name).toList();

        assertThat(names).contains("chat_tool");
        assertThat(names).doesNotContain("read_file");
    }

    @Test
    void sandboxRequestIncludesWorkspaceTools() {
        var service = serviceWith(
                new StubTool("chat_tool", false, true),
                new StubTool("read_file", true, true));

        var names = service.availableTools("sandbox-1").stream().map(AgentService.ToolSummary::name).toList();

        assertThat(names).contains("chat_tool", "read_file");
    }

    @Test
    void githubProjectSandboxAdvertisesAllGithubTools() {
        // A GitHub project chat is always sandbox-bound and clones via the connected GitHub App, so
        // both kinds of GitHub tool must reach the model: the workspace-scoped git tools (which
        // require a sandbox) and the GitHub App integration tools (available while the App is
        // connected). Stub one of each plus a never-available github tool to prove the gates.
        var service = serviceWith(
                new StubTool("git_status", true, true),
                new StubTool("git_commit", true, true),
                new StubTool("git_push", true, true),
                new StubTool("github_create_pull_request", false, true),
                new StubTool("github_create_issue", false, true),
                new StubTool("github_list_repositories", false, true),
                new StubTool("github_disconnected_tool", false, false));

        var names = service.availableTools("sandbox-1").stream().map(AgentService.ToolSummary::name).toList();

        assertThat(names).contains(
                "git_status", "git_commit", "git_push",
                "github_create_pull_request", "github_create_issue", "github_list_repositories");
        assertThat(names).doesNotContain("github_disconnected_tool");
    }

    @Test
    void unavailableIntegrationToolIsNeverAdvertised() {
        var service = serviceWith(
                new StubTool("chat_tool", false, true),
                new StubTool("web_search", false, false));

        assertThat(service.availableTools(null).stream().map(AgentService.ToolSummary::name).toList())
                .contains("chat_tool")
                .doesNotContain("web_search");
        assertThat(service.availableTools("sandbox-1").stream().map(AgentService.ToolSummary::name).toList())
                .doesNotContain("web_search");
    }

    private record StubTool(String toolName, boolean workspace, boolean available) implements LocalTool {
        @Override
        public String name() {
            return toolName;
        }

        @Override
        public String description() {
            return "stub";
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public boolean requiresWorkspace() {
            return workspace;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String execute(Map<String, Object> arguments) {
            return "ok";
        }
    }
}
