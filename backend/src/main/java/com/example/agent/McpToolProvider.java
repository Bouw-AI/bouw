package com.example.agent;

import com.example.agent.model.AvailableTool;

import java.util.List;
import java.util.Map;

/**
 * Abstraction over the MCP server registry.
 * Implemented in mcp-integration so that agent-core has no dependency on the MCP SDK.
 */
public interface McpToolProvider {

    /** Returns every tool exposed by every connected MCP server, keyed by server name. */
    Map<String, List<AvailableTool>> getAllToolsByServer();

    /**
     * Invokes a tool on the named server and returns the result as a plain string.
     *
     * @param serverName  name of the MCP server that owns the tool
     * @param toolName    name of the tool to call
     * @param arguments   key/value arguments as parsed from the model's tool-call JSON
     */
    String callTool(String serverName, String toolName, Map<String, Object> arguments);
}
