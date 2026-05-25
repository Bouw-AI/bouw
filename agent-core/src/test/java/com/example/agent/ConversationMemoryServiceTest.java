package com.example.agent;

import com.example.agent.model.ChatMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the short-term conversation memory over the real {@link InMemoryConversationStore}:
 * recall/record, per-session isolation, sliding-window trimming, statelessness without a session
 * id, and TTL eviction of idle sessions.
 */
class ConversationMemoryServiceTest {

    private static ConversationMemoryService service(int maxMessages, Duration ttl) {
        var props = new ConversationMemoryProperties(true, maxMessages, ttl);
        return new ConversationMemoryService(new InMemoryConversationStore(props), props);
    }

    @Test
    void recordsAndReplaysTurnsForASession() {
        var service = service(20, Duration.ofHours(1));
        service.record("s1", "hello", "hi there");

        List<ChatMessage> history = service.history("s1");
        assertThat(history).hasSize(2);
        assertThat(history.get(0).role()).isEqualTo("user");
        assertThat(history.get(0).content()).isEqualTo("hello");
        assertThat(history.get(1).role()).isEqualTo("assistant");
        assertThat(history.get(1).content()).isEqualTo("hi there");
    }

    @Test
    void keepsSessionsIsolated() {
        var service = service(20, Duration.ofHours(1));
        service.record("a", "from a", "answer a");
        service.record("b", "from b", "answer b");

        assertThat(service.history("a")).extracting(ChatMessage::content)
                .containsExactly("from a", "answer a");
        assertThat(service.history("b")).extracting(ChatMessage::content)
                .containsExactly("from b", "answer b");
    }

    @Test
    void trimsToSlidingWindow() {
        var service = service(4, Duration.ofHours(1)); // keep last 4 messages = 2 turns
        service.record("s", "q1", "a1");
        service.record("s", "q2", "a2");
        service.record("s", "q3", "a3");

        assertThat(service.history("s")).extracting(ChatMessage::content)
                .containsExactly("q2", "a2", "q3", "a3");
    }

    @Test
    void isStatelessWithoutASessionId() {
        var service = service(20, Duration.ofHours(1));
        service.record(null, "hello", "hi");
        service.record("  ", "hello", "hi");

        assertThat(service.history(null)).isEmpty();
        assertThat(service.history("missing")).isEmpty();
    }

    @Test
    void doesNotRecordBlankAnswers() {
        var service = service(20, Duration.ofHours(1));
        service.record("s", "hello", "");
        service.record("s", "hello", null);

        assertThat(service.history("s")).isEmpty();
    }

    @Test
    void evictsExpiredSessions() throws InterruptedException {
        var service = service(20, Duration.ofMillis(50));
        service.record("s", "hello", "hi");
        assertThat(service.history("s")).hasSize(2);

        Thread.sleep(200);
        assertThat(service.history("s")).isEmpty();
    }
}
