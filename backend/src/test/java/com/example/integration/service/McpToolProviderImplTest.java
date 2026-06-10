package com.example.integration.service;

import com.example.agent.model.AvailableTool;
import com.example.mcpclient.service.McpServerRegistryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpToolProviderImplTest {

    @Mock
    McpServerRegistryService registry;

    ObjectMapper objectMapper = new ObjectMapper();
    McpToolProviderImpl provider;

    @BeforeEach
    void setUp() {
        provider = new McpToolProviderImpl(registry, objectMapper);
    }

    @Test
    void getAllToolsByServerConvertsTools() {
        var schema = new McpSchema.JsonSchema(
                "object",
                Map.of("tz", Map.of("type", "string")),
                List.of(),
                null, null, null
        );
        var tool = new McpSchema.Tool("get_time", null, "Get current time", schema, null, null, null);
        when(registry.getAllToolsByServer()).thenReturn(Map.of("server1", List.of(tool)));

        Map<String, List<AvailableTool>> result = provider.getAllToolsByServer();

        assertThat(result).containsKey("server1");
        assertThat(result.get("server1")).hasSize(1);
        AvailableTool converted = result.get("server1").get(0);
        assertThat(converted.name()).isEqualTo("get_time");
        assertThat(converted.description()).isEqualTo("Get current time");
        assertThat(converted.inputSchema()).isNotNull();
    }

    @Test
    void getAllToolsByServerWithNullSchema() {
        var tool = new McpSchema.Tool("ping", null, "Ping the server", null, null, null, null);
        when(registry.getAllToolsByServer()).thenReturn(Map.of("server1", List.of(tool)));

        Map<String, List<AvailableTool>> result = provider.getAllToolsByServer();

        assertThat(result.get("server1")).hasSize(1);
        AvailableTool converted = result.get("server1").get(0);
        assertThat(converted.name()).isEqualTo("ping");
        // Should fall back to a default schema (not null)
        assertThat(converted.inputSchema()).isNotNull();
        assertThat(converted.inputSchema()).containsEntry("type", "object");
    }

    @Test
    void callToolDelegatesToRegistry() {
        when(registry.callTool("server1", "get_time", Map.of())).thenReturn("12:00 UTC");

        String result = provider.callTool("server1", "get_time", Map.of());

        assertThat(result).isEqualTo("12:00 UTC");
    }

    @Test
    void getAllToolsByServerReturnsEmptyWhenNoServers() {
        when(registry.getAllToolsByServer()).thenReturn(Map.of());

        Map<String, List<AvailableTool>> result = provider.getAllToolsByServer();

        assertThat(result).isEmpty();
    }

    @Test
    void getAllToolsByServerHandlesMultipleServers() {
        var tool1 = new McpSchema.Tool("tool_a", null, "Tool A", null, null, null, null);
        var tool2 = new McpSchema.Tool("tool_b", null, "Tool B", null, null, null, null);
        var tool3 = new McpSchema.Tool("tool_c", null, "Tool C", null, null, null, null);
        when(registry.getAllToolsByServer()).thenReturn(Map.of(
                "server1", List.of(tool1, tool2),
                "server2", List.of(tool3)
        ));

        Map<String, List<AvailableTool>> result = provider.getAllToolsByServer();

        assertThat(result).containsKeys("server1", "server2");
        assertThat(result.get("server1")).hasSize(2);
        assertThat(result.get("server2")).hasSize(1);
        assertThat(result.get("server1")).extracting(AvailableTool::name)
                .containsExactlyInAnyOrder("tool_a", "tool_b");
        assertThat(result.get("server2").get(0).name()).isEqualTo("tool_c");
    }

    @Test
    void callToolPassesArgumentsToRegistry() {
        Map<String, Object> args = Map.of("timezone", "Asia/Tokyo");
        when(registry.callTool("time-server", "get_time", args)).thenReturn("17:00 JST");

        String result = provider.callTool("time-server", "get_time", args);

        assertThat(result).isEqualTo("17:00 JST");
    }

    @Test
    void getAllToolsByServerPreservesInputSchemaProperties() {
        var schema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "city", Map.of("type", "string", "description", "The city name"),
                        "units", Map.of("type", "string", "enum", List.of("celsius", "fahrenheit"))
                ),
                List.of("city"),
                null, null, null
        );
        var tool = new McpSchema.Tool("get_weather", null, "Get weather", schema, null, null, null);
        when(registry.getAllToolsByServer()).thenReturn(Map.of("weather-server", List.of(tool)));

        Map<String, List<AvailableTool>> result = provider.getAllToolsByServer();

        AvailableTool converted = result.get("weather-server").get(0);
        assertThat(converted.inputSchema()).containsKey("type");
        assertThat(converted.inputSchema()).containsKey("properties");
    }
}
