package com.example.mcpclient;

import com.example.mcpclient.model.McpServersConfig;
import com.example.mcpclient.model.McpServerDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        // Point to a non-existent file so no real servers are started during tests
        "mcp.config-file=./nonexistent-test-servers.json"
})
class McpClientApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context starts successfully with no servers configured
    }

    @Test
    void configSerializationRoundTrip(@TempDir Path tmpDir) throws Exception {
        ObjectMapper mapper = new ObjectMapper().enable(
                com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);

        McpServersConfig original = new McpServersConfig(Map.of(
                "filesystem", new McpServerDefinition(
                        "npx",
                        List.of("-y", "@modelcontextprotocol/server-filesystem", "/tmp"),
                        Map.of(),
                        null,
                        null
                ),
                "my-sse-server", new McpServerDefinition(
                        null, null, null,
                        "http://localhost:3000/sse",
                        null
                )
        ));

        File file = tmpDir.resolve("mcp-servers.json").toFile();
        mapper.writeValue(file, original);

        McpServersConfig loaded = mapper.readValue(file, McpServersConfig.class);
        assertThat(loaded.mcpServers()).containsKey("filesystem");
        assertThat(loaded.mcpServers()).containsKey("my-sse-server");
        assertThat(loaded.mcpServers().get("filesystem").command()).isEqualTo("npx");
        assertThat(loaded.mcpServers().get("my-sse-server").resolvedType())
                .isEqualTo(McpServerDefinition.ServerType.SSE);
    }
}
