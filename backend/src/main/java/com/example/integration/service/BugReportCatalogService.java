package com.example.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class BugReportCatalogService {

    private static final Logger log = LoggerFactory.getLogger(BugReportCatalogService.class);
    private static final DateTimeFormatter DIRECTORY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration ttl;
    private final String keyPrefix;

    @Autowired
    public BugReportCatalogService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${bug-reports.redis.ttl:1d}") Duration ttl,
            @Value("${bug-reports.redis.key-prefix:bug-reports}") String keyPrefix) {
        this(redis, objectMapper, Clock.systemDefaultZone(), ttl, keyPrefix);
    }

    BugReportCatalogService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            Clock clock,
            Duration ttl,
            String keyPrefix) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.ttl = ttl;
        this.keyPrefix = keyPrefix;
    }

    public Optional<StoredBugReport> save(
            String owner,
            String title,
            String sessionId,
            String agentId,
            String sandboxId,
            String relativePath,
            String reportBody) {
        Instant now = Instant.now(clock);
        String id = UUID.randomUUID().toString();
        StoredBugReport record = new StoredBugReport(
                id,
                blankFallback(title, "Untitled chat"),
                sessionId,
                agentId,
                sandboxId,
                relativePath,
                reportBody,
                now.toString());
        try {
            String json = objectMapper.writeValueAsString(record);
            redis.opsForValue().set(reportKey(owner, id), json, ttl);
            redis.opsForZSet().add(indexKey(owner), id, now.toEpochMilli());
            return Optional.of(record);
        } catch (Exception e) {
            log.warn("Could not persist bug report {} to Redis: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    public List<StoredBugReportSummary> list(String owner) {
        try {
            var ids = redis.opsForZSet().reverseRange(indexKey(owner), 0, 49);
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            List<StoredBugReportSummary> result = new ArrayList<>();
            List<String> expired = new ArrayList<>();
            for (String id : ids) {
                String json = redis.opsForValue().get(reportKey(owner, id));
                if (json == null || json.isBlank()) {
                    expired.add(id);
                    continue;
                }
                StoredBugReport record = objectMapper.readValue(json, StoredBugReport.class);
                result.add(new StoredBugReportSummary(
                        record.id(),
                        record.title(),
                        record.relativePath(),
                        record.createdAt()));
            }
            if (!expired.isEmpty()) {
                redis.opsForZSet().remove(indexKey(owner), expired.toArray());
            }
            result.sort(Comparator.comparing(StoredBugReportSummary::createdAt).reversed());
            return result;
        } catch (Exception e) {
            log.warn("Could not list bug reports from Redis: {}", e.getMessage());
            return List.of();
        }
    }

    public Optional<StoredBugReport> find(String owner, String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(reportKey(owner, id));
            if (json == null || json.isBlank()) {
                redis.opsForZSet().remove(indexKey(owner), id);
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, StoredBugReport.class));
        } catch (Exception e) {
            log.warn("Could not read bug report {} from Redis: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    public String materializedRelativePath(StoredBugReport report) {
        if (report == null) {
            return "bug-reports/imported/bug-report.txt";
        }
        if (report.relativePath() != null && !report.relativePath().isBlank()) {
            return report.relativePath();
        }
        Instant created = parseInstant(report.createdAt()).orElse(Instant.now(clock));
        LocalDateTime local = LocalDateTime.ofInstant(created, ZoneId.systemDefault());
        String dateDir = DIRECTORY_FORMAT.format(LocalDate.from(local));
        return "bug-reports/" + dateDir + "/" + slug(report.title()) + ".txt";
    }

    private String reportKey(String owner, String id) {
        return keyPrefix + ":report:" + encodeOwner(owner) + ":" + id;
    }

    private String indexKey(String owner) {
        return keyPrefix + ":index:" + encodeOwner(owner);
    }

    private static String encodeOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            return "global";
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(owner.getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<Instant> parseInstant(String value) {
        try {
            return Optional.of(Instant.parse(value));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static String slug(String title) {
        String normalized = blankFallback(title, "bug-report")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? "bug-report" : normalized;
    }

    private static String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record StoredBugReport(
            String id,
            String title,
            String sessionId,
            String agentId,
            String sandboxId,
            String relativePath,
            String content,
            String createdAt) {}

    public record StoredBugReportSummary(
            String id,
            String title,
            String relativePath,
            String createdAt) {}
}
