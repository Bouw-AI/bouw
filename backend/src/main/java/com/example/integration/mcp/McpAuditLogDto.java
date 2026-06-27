package com.example.integration.mcp;

import java.time.Instant;

/**
 * API view of one MCP tool-invocation audit record. Scoped to the authenticated owner. Carries only
 * the bounded previews already stored (no secrets); {@code serverName} is the server's display name
 * resolved at read time, or {@code null} if the server has since been removed.
 */
public record McpAuditLogDto(
        String id,
        String serverId,
        String serverName,
        String toolName,
        String status,
        String argumentsPreview,
        String resultPreview,
        Instant createdAt) {
}
