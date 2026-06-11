package com.example.agent.model;

import java.util.Map;

/** A tool discovered from an MCP server, converted to a provider-agnostic form. */
public record AvailableTool(
        String name,
        String description,
        Map<String, Object> inputSchema
) {}
