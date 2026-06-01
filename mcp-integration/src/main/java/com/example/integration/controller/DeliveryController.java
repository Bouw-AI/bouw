package com.example.integration.controller;

import com.example.integration.service.OutboxResultDelivery;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Outbound delivery stream for scheduled-prompt results.
 *
 * <p>{@code GET /api/agent/deliveries} is a long-lived Server-Sent Events stream. Delivery clients
 * (e.g. the Discord bot) subscribe once and receive a {@code delivery} event — {@code {target,
 * prompt, result, timestamp}} — each time a scheduled prompt finishes. The client routes the result
 * to the origin identified by {@code target} (e.g. a Discord channel/DM).
 *
 * <p>Lives under {@code /api/agent/**}, so it inherits the same API-key / localhost protection as the
 * other agent endpoints.
 */
@RestController
@RequestMapping("/api/agent/deliveries")
public class DeliveryController {

    // Effectively indefinite; the client reconnects if the stream is ever torn down.
    private static final long STREAM_TIMEOUT_MILLIS = 0L;

    private final OutboxResultDelivery outbox;

    public DeliveryController(OutboxResultDelivery outbox) {
        this.outbox = outbox;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return outbox.subscribe(STREAM_TIMEOUT_MILLIS);
    }

    @GetMapping("/recent")
    public List<Map<String, Object>> recent() {
        return outbox.recent();
    }
}
