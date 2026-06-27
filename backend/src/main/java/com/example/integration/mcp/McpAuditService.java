package com.example.integration.mcp;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read access to the MCP tool-invocation audit trail for the Integrations UI.
 *
 * <p>Phase 1 began writing {@code mcp_audit_log} on every tool call; this surfaces it. Every query is
 * scoped to the authenticated owner, so a user only ever sees their own activity. Server ids are
 * resolved to display names from the user's own servers; an id that no longer resolves (server
 * removed) is left {@code null}.
 */
@Service
public class McpAuditService {

    /** Hard cap on how many records a single request may return. */
    static final int MAX_LIMIT = 200;
    private static final int DEFAULT_LIMIT = 50;

    private final McpAuditLogRepository auditLogRepository;
    private final McpServerRepository serverRepository;

    public McpAuditService(McpAuditLogRepository auditLogRepository, McpServerRepository serverRepository) {
        this.auditLogRepository = auditLogRepository;
        this.serverRepository = serverRepository;
    }

    /** The owner's most recent tool invocations, newest first, bounded by {@code limit}. */
    public List<McpAuditLogDto> recent(String owner, Integer limit) {
        int bounded = clampLimit(limit);
        Map<String, String> serverNames = serverRepository.findByOwner(owner).stream()
                .collect(Collectors.toMap(McpServerEntity::id, McpServerEntity::displayName, (a, b) -> a));
        return auditLogRepository.findByOwner(owner, bounded).stream()
                .map(toDto(serverNames))
                .toList();
    }

    private static Function<McpAuditLogEntity, McpAuditLogDto> toDto(Map<String, String> serverNames) {
        return entry -> new McpAuditLogDto(
                entry.id(),
                entry.serverId(),
                entry.serverId() == null ? null : serverNames.get(entry.serverId()),
                entry.toolName(),
                entry.status(),
                entry.argumentsJson(),
                entry.resultPreview(),
                entry.createdAt());
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
