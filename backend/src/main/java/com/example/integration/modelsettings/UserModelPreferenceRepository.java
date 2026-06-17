package com.example.integration.modelsettings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Repository
public class UserModelPreferenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserModelPreferenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Set<String> findEnabledModelIds(String ownerUsername) {
        List<String> ids = jdbcTemplate.query(
                """
                        select model_id
                        from user_model_preferences
                        where owner_username = ? and enabled = true
                        """,
                (rs, rowNum) -> rs.getString("model_id"),
                ownerUsername);
        return new LinkedHashSet<>(ids);
    }

    public int countRowsForOwner(String ownerUsername) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from user_model_preferences where owner_username = ?",
                Integer.class,
                ownerUsername);
        return count == null ? 0 : count;
    }

    public void save(String ownerUsername, String modelId, boolean enabled, Instant updatedAt) {
        int updated = jdbcTemplate.update(
                """
                        update user_model_preferences
                        set enabled = ?, updated_at = ?
                        where owner_username = ? and model_id = ?
                        """,
                enabled,
                timestamp(updatedAt),
                ownerUsername,
                modelId);
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                            insert into user_model_preferences (owner_username, model_id, enabled, updated_at)
                            values (?, ?, ?, ?)
                            """,
                    ownerUsername,
                    modelId,
                    enabled,
                    timestamp(updatedAt));
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
