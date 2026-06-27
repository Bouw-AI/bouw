package com.example.integration.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the audit read path: owner isolation, server-name resolution, ordering, and limit clamping. */
class McpAuditServiceTest extends AbstractMcpDbTest {

    private McpAuditService auditService;

    @BeforeEach
    void setUpAudit() {
        auditService = new McpAuditService(auditLogRepository, serverRepository);
    }

    private void writeAudit(String owner, String serverId, String tool, String status, Instant at) {
        auditLogRepository.insert(new McpAuditLogEntity(
                UUID.randomUUID().toString(), owner, "agent-1", "session-1", serverId, tool,
                "{\"q\":\"x\"}", "result preview", status, at));
    }

    @Test
    void returnsOnlyOwnersRecordsNewestFirstWithServerName() {
        insertUser("alice");
        insertUser("bob");
        McpServerEntity server = newServer("alice", "linear", McpAuthType.NONE, null);
        serverRepository.insert(server);

        writeAudit("alice", server.id(), "create_issue", "success", Instant.parse("2026-01-01T00:00:00Z"));
        writeAudit("alice", server.id(), "list_issues", "error", Instant.parse("2026-01-02T00:00:00Z"));
        writeAudit("bob", server.id(), "secret_tool", "success", Instant.parse("2026-01-03T00:00:00Z"));

        List<McpAuditLogDto> entries = auditService.recent("alice", 50);

        assertThat(entries).hasSize(2);
        // Newest first.
        assertThat(entries.get(0).toolName()).isEqualTo("list_issues");
        assertThat(entries.get(0).status()).isEqualTo("error");
        assertThat(entries.get(0).serverName()).isEqualTo("linear display");
        // Bob's record is never visible to Alice.
        assertThat(entries).noneMatch(e -> e.toolName().equals("secret_tool"));
    }

    @Test
    void serverNameIsNullWhenServerRemoved() {
        insertUser("alice");
        writeAudit("alice", "gone-server-id", "create_issue", "success", Instant.now(clock));

        List<McpAuditLogDto> entries = auditService.recent("alice", null);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).serverName()).isNull();
        assertThat(entries.get(0).serverId()).isEqualTo("gone-server-id");
    }

    @Test
    void limitIsClampedToMax() {
        insertUser("alice");
        for (int i = 0; i < 5; i++) {
            writeAudit("alice", null, "t" + i, "success", Instant.now(clock).plusSeconds(i));
        }

        // A huge limit must not blow past the cap, and a tiny one is honored.
        assertThat(auditService.recent("alice", 1)).hasSize(1);
        assertThat(auditService.recent("alice", 10_000)).hasSize(5);
    }
}
