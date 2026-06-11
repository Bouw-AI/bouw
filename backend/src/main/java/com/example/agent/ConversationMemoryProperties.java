package com.example.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for short-term, per-session conversation memory (the sliding window of recent turns
 * replayed into the model on each request).
 *
 * <p>Enabled by default. The {@code enabled} flag is consumed by the {@code @ConditionalOnProperty}
 * on {@link ConversationMemoryService} / {@link InMemoryConversationStore}, not read here.
 *
 * @param enabled     master switch for short-term memory (default true)
 * @param maxMessages sliding-window size: the most recent N stored messages kept per session
 * @param ttl         how long an idle session's history is retained before eviction
 */
@ConfigurationProperties("conversation.memory")
public record ConversationMemoryProperties(boolean enabled, int maxMessages, Duration ttl) {

    public ConversationMemoryProperties {
        if (maxMessages <= 0) {
            maxMessages = 20;
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            ttl = Duration.ofHours(2);
        }
    }
}
