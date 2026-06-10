package com.example.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Redis-backed long-term memory.
 *
 * <p>When {@code enabled} is false (the default) the agent behaves exactly as before and neither
 * Redis nor the embedding endpoint is contacted.
 *
 * @param enabled    master switch for the memory feature
 * @param keyPrefix  Redis key prefix under which memory records are stored
 * @param topK       number of most-similar past memories recalled into the prompt
 * @param minScore   minimum cosine similarity (0..1) a memory must reach to be recalled; 0 disables the threshold
 * @param maxEntries hard cap on stored memories; the oldest are evicted past this
 */
@ConfigurationProperties("memory")
public record MemoryProperties(boolean enabled, String keyPrefix, int topK, double minScore, int maxEntries) {

    public MemoryProperties {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            keyPrefix = "agent:memory";
        }
        if (topK <= 0) {
            topK = 3;
        }
        if (maxEntries <= 0) {
            maxEntries = 1000;
        }
    }
}
