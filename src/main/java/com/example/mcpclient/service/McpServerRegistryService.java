package com.example.mcpclient.service;

import com.example.mcpclient.config.McpProperties;
import com.example.mcpclient.model.McpServerDefinition;
import com.example.mcpclient.model.McpServersConfig;
import com.example.mcpclient.model.ServerInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class McpServerRegistryService {

    private static final Logger log = LoggerFactory.getLogger(McpServerRegistryService.class);

    private static final McpSchema.Implementation CLIENT_INFO =
            new McpSchema.Implementation("spring-mcp-client", "1.0.0");

    private final McpProperties properties;
    private final ObjectMapper objectMapper;
    private final McpJsonMapper mcpJsonMapper;

    private final Map<String, McpSyncClient> activeClients = new ConcurrentHashMap<>();
    private final Map<String, String> connectionErrors = new ConcurrentHashMap<>();

    public McpServerRegistryService(McpProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.mcpJsonMapper = McpJsonDefaults.getMapper();
    }

    @PostConstruct
    public void init() {
        McpServersConfig config = loadConfig();
        config.mcpServers().forEach((name, def) -> {
            log.debug("Connecting to MCP server: {}", name);
            connectServer(name, def);
        });
    }

    @PreDestroy
    public void shutdown() {
        activeClients.forEach((name, client) -> {
            try {
                client.close();
                log.debug("Closed MCP client for server: {}", name);
            } catch (Exception e) {
                log.warn("Error closing client for '{}': {}", name, e.getMessage());
            }
        });
        activeClients.clear();
    }

    public List<ServerInfo> listServers() {
        McpServersConfig config = loadConfig();
        return config.mcpServers().entrySet().stream()
                .map(e -> buildServerInfo(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    public ServerInfo getServer(String name) {
        McpServersConfig config = loadConfig();
        McpServerDefinition def = config.mcpServers().get(name);
        if (def == null) {
            throw new NoSuchElementException("Server not found: " + name);
        }
        return buildServerInfo(name, def);
    }

    /**
     * Adds or replaces a server entry in {@code mcp-servers.json} and connects
     * immediately. If a client with the same name already exists it is closed
     * before the new one is created.
     */
    public ServerInfo addServer(String name, McpServerDefinition definition) {
        McpServersConfig config = loadConfig();
        Map<String, McpServerDefinition> updated = new LinkedHashMap<>(config.mcpServers());
        updated.put(name, definition);
        saveConfig(new McpServersConfig(updated));

        disconnectServer(name);
        connectServer(name, definition);

        return buildServerInfo(name, definition);
    }

    /**
     * Removes a server entry from {@code mcp-servers.json} and disconnects its
     * client if one is active.
     */
    public void removeServer(String name) {
        McpServersConfig config = loadConfig();
        if (!config.mcpServers().containsKey(name)) {
            throw new NoSuchElementException("Server not found: " + name);
        }
        Map<String, McpServerDefinition> updated = new LinkedHashMap<>(config.mcpServers());
        updated.remove(name);
        saveConfig(new McpServersConfig(updated));
        disconnectServer(name);
    }

    /** Closes and re-opens the connection without changing the persisted definition. */
    public ServerInfo reconnect(String name) {
        McpServersConfig config = loadConfig();
        McpServerDefinition def = config.mcpServers().get(name);
        if (def == null) {
            throw new NoSuchElementException("Server not found: " + name);
        }
        disconnectServer(name);
        connectServer(name, def);
        return buildServerInfo(name, def);
    }

    public List<ServerInfo.ToolInfo> listTools(String name) {
        McpSyncClient client = activeClients.get(name);
        if (client == null) {
            throw new IllegalStateException("Server '" + name + "' is not connected");
        }
        McpSchema.ListToolsResult result = client.listTools();
        return result.tools().stream()
                .map(t -> new ServerInfo.ToolInfo(t.name(), t.description()))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void connectServer(String name, McpServerDefinition def) {
        try {
            McpSyncClient client = buildClient(def);
            client.initialize();
            activeClients.put(name, client);
            connectionErrors.remove(name);
            log.info("Connected to MCP server '{}'", name);
        } catch (Exception e) {
            connectionErrors.put(name, e.getMessage());
            log.warn("Failed to connect to MCP server '{}': {}", name, e.getMessage());
        }
    }

    private void disconnectServer(String name) {
        McpSyncClient existing = activeClients.remove(name);
        if (existing != null) {
            try {
                existing.close();
            } catch (Exception e) {
                log.warn("Error closing client for '{}': {}", name, e.getMessage());
            }
        }
        connectionErrors.remove(name);
    }

    private McpSyncClient buildClient(McpServerDefinition def) {
        return switch (def.resolvedType()) {
            case STDIO -> buildStdioClient(def);
            case SSE -> buildSseClient(def);
        };
    }

    private McpSyncClient buildStdioClient(McpServerDefinition def) {
        if (def.command() == null || def.command().isBlank()) {
            throw new IllegalArgumentException("'command' is required for stdio servers");
        }
        ServerParameters.Builder paramsBuilder = ServerParameters.builder(def.command());
        if (def.args() != null && !def.args().isEmpty()) {
            paramsBuilder.args(def.args());
        }
        if (def.env() != null && !def.env().isEmpty()) {
            paramsBuilder.env(def.env());
        }
        StdioClientTransport transport =
                new StdioClientTransport(paramsBuilder.build(), mcpJsonMapper);
        return McpClient.sync(transport).clientInfo(CLIENT_INFO).build();
    }

    private McpSyncClient buildSseClient(McpServerDefinition def) {
        if (def.url() == null || def.url().isBlank()) {
            throw new IllegalArgumentException("'url' is required for SSE servers");
        }
        HttpClientSseClientTransport transport =
                HttpClientSseClientTransport.builder(def.url()).build();
        return McpClient.sync(transport).clientInfo(CLIENT_INFO).build();
    }

    private ServerInfo buildServerInfo(String name, McpServerDefinition def) {
        boolean connected = activeClients.containsKey(name);
        String error = connectionErrors.get(name);
        List<ServerInfo.ToolInfo> tools = null;

        if (connected) {
            try {
                tools = listTools(name);
            } catch (Exception e) {
                log.debug("Could not fetch tools for '{}': {}", name, e.getMessage());
            }
        }

        return new ServerInfo(name, def, connected, error, tools);
    }

    // -------------------------------------------------------------------------
    // Config file I/O
    // -------------------------------------------------------------------------

    McpServersConfig loadConfig() {
        Path path = Path.of(properties.resolvedConfigFile());
        if (!Files.exists(path)) {
            log.info("Config file '{}' not found – starting with empty config", path);
            return McpServersConfig.empty();
        }
        try {
            return objectMapper.readValue(path.toFile(), McpServersConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read MCP config from " + path, e);
        }
    }

    void saveConfig(McpServersConfig config) {
        Path path = Path.of(properties.resolvedConfigFile());
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writeValue(path.toFile(), config);
            log.debug("Saved MCP config to '{}'", path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write MCP config to " + path, e);
        }
    }
}
