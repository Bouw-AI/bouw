package com.example.integration.controller;

import com.example.mcpclient.model.McpServerDefinition;
import com.example.mcpclient.model.ServerInfo;
import com.example.mcpclient.service.McpServerRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemStatusControllerTest {

    @Mock McpServerRegistryService registry;

    SystemStatusController controller;

    @BeforeEach
    void setUp() {
        controller = new SystemStatusController(registry, "openrouter", "openai/gpt-oss-120b", false, "agent:memory", 3);
    }

    @Test
    void getStatus_returnsFourServices() {
        when(registry.listServers()).thenReturn(List.of());
        assertThat(controller.getStatus()).hasSize(4);
    }

    @Test
    void getStatus_agentIsAlwaysUp() {
        when(registry.listServers()).thenReturn(List.of());
        var agent = controller.getStatus().stream().filter(s -> "agent".equals(s.key())).findFirst().orElseThrow();
        assertThat(agent.status()).isEqualTo("up");
        assertThat(agent.meta()).startsWith("uptime");
    }

    @Test
    void getStatus_llmShowsProviderAndModel() {
        when(registry.listServers()).thenReturn(List.of());
        var llm = controller.getStatus().stream().filter(s -> "llm".equals(s.key())).findFirst().orElseThrow();
        assertThat(llm.status()).isEqualTo("up");
        assertThat(llm.detail()).contains("openrouter").contains("openai/gpt-oss-120b");
    }

    @Test
    void getStatus_memoryIsDownWhenDisabled() {
        when(registry.listServers()).thenReturn(List.of());
        var memory = controller.getStatus().stream().filter(s -> "memory".equals(s.key())).findFirst().orElseThrow();
        assertThat(memory.status()).isEqualTo("down");
        assertThat(memory.detail()).contains("disabled");
    }

    @Test
    void getStatus_memoryIsUpWhenEnabled() {
        var ctrl = new SystemStatusController(registry, "ollama", "llama3.2", true, "agent:memory", 3);
        when(registry.listServers()).thenReturn(List.of());
        var memory = ctrl.getStatus().stream().filter(s -> "memory".equals(s.key())).findFirst().orElseThrow();
        assertThat(memory.status()).isEqualTo("up");
        assertThat(memory.detail()).contains("Redis");
    }

    @Test
    void getStatus_searchIsUpWhenWebSearchServerConnected() {
        var def = new McpServerDefinition("python3", List.of("search.py"), null, null, null);
        when(registry.listServers()).thenReturn(List.of(
                new ServerInfo("web-search", def, true, null, List.of())));
        var search = controller.getStatus().stream().filter(s -> "search".equals(s.key())).findFirst().orElseThrow();
        assertThat(search.status()).isEqualTo("up");
    }

    @Test
    void getStatus_searchIsErrorWhenServerDisconnected() {
        var def = new McpServerDefinition("python3", List.of("search.py"), null, null, null);
        when(registry.listServers()).thenReturn(List.of(
                new ServerInfo("web-search", def, false, "connection refused", List.of())));
        var search = controller.getStatus().stream().filter(s -> "search".equals(s.key())).findFirst().orElseThrow();
        assertThat(search.status()).isEqualTo("error");
    }

    @Test
    void getStatus_searchIsDownWhenNotConfigured() {
        when(registry.listServers()).thenReturn(List.of());
        var search = controller.getStatus().stream().filter(s -> "search".equals(s.key())).findFirst().orElseThrow();
        assertThat(search.status()).isEqualTo("down");
        assertThat(search.detail()).isEqualTo("not configured");
    }
}
