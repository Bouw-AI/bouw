package com.example.integration.service;

import com.example.agent.sandbox.SandboxSession;
import com.example.agent.sandbox.SandboxStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Postgres-backed persistence for {@link SandboxSession} over the {@code sandbox_sessions} table. */
@Repository
public class SandboxSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public SandboxSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<SandboxSession> MAPPER = (rs, rowNum) -> new SandboxSession(
            UUID.fromString(rs.getString("id")),
            rs.getString("chat_session_id") == null ? null : UUID.fromString(rs.getString("chat_session_id")),
            rs.getString("container_id"),
            rs.getString("container_name"),
            rs.getString("docker_volume_name"),
            rs.getString("repository_url"),
            rs.getString("repository_branch"),
            rs.getString("repository_path"),
            SandboxStatus.valueOf(rs.getString("status")),
            instant(rs.getTimestamp("created_at")),
            instant(rs.getTimestamp("last_used_at")),
            instant(rs.getTimestamp("expires_at")));

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    /** Inserts or updates the session row. */
    public void save(SandboxSession session) {
        int updated = jdbcTemplate.update("""
                update sandbox_sessions set
                    chat_session_id = ?, container_id = ?, container_name = ?, docker_volume_name = ?,
                    repository_url = ?, repository_branch = ?, repository_path = ?, status = ?,
                    last_used_at = ?, expires_at = ?
                where id = ?
                """,
                idText(session.chatSessionId()), session.containerId(), session.containerName(),
                session.dockerVolumeName(), session.repositoryUrl(), session.repositoryBranch(),
                session.repositoryPath(), session.status().name(),
                ts(session.lastUsedAt()), ts(session.expiresAt()), session.id().toString());
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into sandbox_sessions
                        (id, chat_session_id, container_id, container_name, docker_volume_name,
                         repository_url, repository_branch, repository_path, status,
                         created_at, last_used_at, expires_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    session.id().toString(), idText(session.chatSessionId()), session.containerId(),
                    session.containerName(), session.dockerVolumeName(), session.repositoryUrl(),
                    session.repositoryBranch(), session.repositoryPath(), session.status().name(),
                    ts(session.createdAt()), ts(session.lastUsedAt()), ts(session.expiresAt()));
        }
    }

    public Optional<SandboxSession> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                "select * from sandbox_sessions where id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<SandboxSession> findByChatSessionId(String chatSessionId) {
        if (chatSessionId == null || chatSessionId.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                "select * from sandbox_sessions where chat_session_id = ? order by created_at desc",
                MAPPER, chatSessionId).stream().findFirst();
    }

    /** Sessions whose {@code expires_at} is at or before {@code cutoff} and not already destroyed. */
    public List<SandboxSession> findExpired(Instant cutoff) {
        return jdbcTemplate.query("""
                select * from sandbox_sessions
                where expires_at is not null and expires_at <= ? and status <> ?
                """, MAPPER, ts(cutoff), SandboxStatus.DESTROYED.name());
    }

    public List<SandboxSession> findAll() {
        return jdbcTemplate.query("select * from sandbox_sessions", MAPPER);
    }

    public void updateStatus(String id, SandboxStatus status) {
        jdbcTemplate.update("update sandbox_sessions set status = ? where id = ?", status.name(), id);
    }

    public void touch(String id, Instant lastUsedAt, Instant expiresAt) {
        jdbcTemplate.update("update sandbox_sessions set last_used_at = ?, expires_at = ? where id = ?",
                ts(lastUsedAt), ts(expiresAt), id);
    }

    public void delete(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        jdbcTemplate.update("delete from sandbox_sessions where id = ?", id);
    }

    private static String idText(UUID id) {
        return id == null ? null : id.toString();
    }
}
