package com.example.agent;

import com.example.agent.model.ChatMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process {@link ConversationStore}: keeps each session's recent turns in a
 * {@link ConcurrentHashMap}. Idle sessions are evicted once they pass {@code ttl}, swept lazily on
 * access so abandoned conversations do not accumulate forever.
 *
 * <p>This is the default and works without any external dependency. It is per-instance only, so
 * sessions are not shared across multiple server instances.
 */
@Component
@ConditionalOnProperty(prefix = "conversation.memory", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InMemoryConversationStore implements ConversationStore {

    private record Entry(List<ChatMessage> messages, Instant lastAccess) {}

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();
    private final Duration ttl;

    public InMemoryConversationStore(ConversationMemoryProperties properties) {
        this.ttl = properties.ttl();
    }

    @Override
    public List<ChatMessage> load(String sessionId) {
        purgeExpired();
        Entry entry = sessions.computeIfPresent(sessionId,
                (id, e) -> new Entry(e.messages(), Instant.now()));
        return entry == null ? List.of() : entry.messages();
    }

    @Override
    public void save(String sessionId, List<ChatMessage> messages) {
        purgeExpired();
        sessions.put(sessionId, new Entry(List.copyOf(messages), Instant.now()));
    }

    private void purgeExpired() {
        Instant cutoff = Instant.now().minus(ttl);
        sessions.values().removeIf(e -> e.lastAccess().isBefore(cutoff));
    }
}
