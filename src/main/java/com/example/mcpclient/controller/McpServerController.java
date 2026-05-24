package com.example.mcpclient.controller;

import com.example.mcpclient.model.McpServerDefinition;
import com.example.mcpclient.model.ServerInfo;
import com.example.mcpclient.service.McpServerRegistryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/servers")
public class McpServerController {

    private final McpServerRegistryService registry;

    public McpServerController(McpServerRegistryService registry) {
        this.registry = registry;
    }

    /** List all configured MCP servers with their connection status and tools. */
    @GetMapping
    public List<ServerInfo> listServers() {
        return registry.listServers();
    }

    /** Get details for a single server. */
    @GetMapping("/{name}")
    public ServerInfo getServer(@PathVariable String name) {
        return registry.getServer(name);
    }

    /**
     * Add or update a server. Persists to {@code mcp-servers.json} and connects
     * immediately.
     *
     * <p>Example body for a stdio server:
     * <pre>{@code
     * {
     *   "command": "npx",
     *   "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
     *   "env": {}
     * }
     * }</pre>
     *
     * <p>Example body for an SSE server:
     * <pre>{@code
     * { "url": "http://localhost:3000/sse" }
     * }</pre>
     */
    @PutMapping("/{name}")
    public ResponseEntity<ServerInfo> addOrUpdateServer(
            @PathVariable String name,
            @RequestBody McpServerDefinition definition) {
        ServerInfo info = registry.addServer(name, definition);
        return ResponseEntity.ok(info);
    }

    /** Remove a server from {@code mcp-servers.json} and disconnect it. */
    @DeleteMapping("/{name}")
    public ResponseEntity<Map<String, String>> removeServer(@PathVariable String name) {
        registry.removeServer(name);
        return ResponseEntity.ok(Map.of("message", "Server '" + name + "' removed"));
    }

    /** Force reconnect a server without changing its definition. */
    @PostMapping("/{name}/reconnect")
    public ServerInfo reconnect(@PathVariable String name) {
        return registry.reconnect(name);
    }

    /** List only the tools exposed by a connected server. */
    @GetMapping("/{name}/tools")
    public List<ServerInfo.ToolInfo> listTools(@PathVariable String name) {
        return registry.listTools(name);
    }

    // -------------------------------------------------------------------------
    // Exception handlers
    // -------------------------------------------------------------------------

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleBadState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }
}
