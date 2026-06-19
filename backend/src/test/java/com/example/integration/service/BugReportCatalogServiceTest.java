package com.example.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BugReportCatalogServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final ZSetOperations<String, String> zsetOps = Mockito.mock(ZSetOperations.class);

    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, LinkedHashSet<String>> sortedSets = new LinkedHashMap<>();

    private BugReportCatalogService service;

    @BeforeEach
    void setUp() {
        Mockito.when(redis.opsForValue()).thenReturn(valueOps);
        Mockito.when(redis.opsForZSet()).thenReturn(zsetOps);
        Mockito.doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOps).set(Mockito.anyString(), Mockito.anyString(), Mockito.any(Duration.class));
        Mockito.when(valueOps.get(Mockito.anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        Mockito.doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            sortedSets.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(value);
            return true;
        }).when(zsetOps).add(Mockito.anyString(), Mockito.anyString(), Mockito.anyDouble());
        Mockito.when(zsetOps.reverseRange(Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong()))
                .thenAnswer(invocation -> sortedSets.getOrDefault(invocation.getArgument(0), new LinkedHashSet<>()));
        Mockito.doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object[] members = invocation.getArguments().length > 1
                    ? java.util.Arrays.copyOfRange(invocation.getArguments(), 1, invocation.getArguments().length)
                    : new Object[0];
            LinkedHashSet<String> set = sortedSets.get(key);
            if (set != null) {
                for (Object member : members) {
                    set.remove(String.valueOf(member));
                }
            }
            return Long.valueOf(members.length);
        }).when(zsetOps).remove(Mockito.anyString(), Mockito.<Object>any());

        service = new BugReportCatalogService(
                redis,
                objectMapper,
                Clock.fixed(Instant.parse("2026-06-18T14:05:06Z"), ZoneOffset.UTC),
                Duration.ofDays(1),
                "bug-reports");
    }

    @Test
    void savesAndListsBugReportsByOwner() {
        var saved = service.save(
                "owner-1",
                "Hung chat",
                "session-1",
                null,
                null,
                "bug-reports/2026-06-18/report.txt",
                "body");

        assertThat(saved).isPresent();
        assertThat(service.list("owner-1"))
                .singleElement()
                .satisfies(report -> {
                    assertThat(report.id()).isEqualTo(saved.get().id());
                    assertThat(report.title()).isEqualTo("Hung chat");
                    assertThat(report.relativePath()).isEqualTo("bug-reports/2026-06-18/report.txt");
                });
    }

    @Test
    void prunesExpiredEntriesFromIndexWhenPayloadIsGone() {
        var saved = service.save(
                "owner-1",
                "Hung chat",
                "session-1",
                null,
                null,
                "bug-reports/2026-06-18/report.txt",
                "body").orElseThrow();
        values.clear();

        assertThat(service.list("owner-1")).isEmpty();
        assertThat(service.find("owner-1", saved.id())).isEmpty();
    }
}
