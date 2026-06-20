package com.example.integration.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class ChatSessionEventBroker {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<ChatSessionEvent>>> listeners =
            new ConcurrentHashMap<>();

    public Runnable subscribe(String sessionId, Consumer<ChatSessionEvent> listener) {
        listeners.computeIfAbsent(sessionId, ignored -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> listeners.computeIfPresent(sessionId, (key, callbacks) -> {
            callbacks.remove(listener);
            return callbacks.isEmpty() ? null : callbacks;
        });
    }

    public void publish(ChatSessionEvent event) {
        var callbacks = listeners.get(event.sessionId());
        if (callbacks == null) {
            return;
        }
        for (Consumer<ChatSessionEvent> callback : callbacks) {
            callback.accept(event);
        }
    }
}
