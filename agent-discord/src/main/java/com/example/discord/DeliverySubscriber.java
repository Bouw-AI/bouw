package com.example.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Subscribes to the server's {@code /api/agent/deliveries} SSE stream and posts each scheduled-prompt
 * result back to its Discord origin via {@link DiscordBotService#deliverScheduledResult}.
 *
 * <p>The server cannot push to Discord directly (the bot connects to the server, not vice versa), so
 * this long-lived subscription is how scheduled results reach a user. The connection is held open on
 * a daemon thread and reconnected with backoff if it drops or the server is briefly unavailable.
 */
@Service
public class DeliverySubscriber {

    private static final Logger log = LoggerFactory.getLogger(DeliverySubscriber.class);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

    private final DiscordProperties properties;
    private final DiscordBotService botService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile boolean running = true;
    private Thread worker;

    public DeliverySubscriber(DiscordProperties properties, DiscordBotService botService,
                              ObjectMapper objectMapper) {
        this.properties = properties;
        this.botService = botService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @PostConstruct
    public void start() {
        worker = Thread.ofVirtual().name("delivery-subscriber").start(this::runLoop);
        log.info("Scheduled-result delivery subscriber started against {}", properties.getServerUrl());
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void runLoop() {
        long backoffSeconds = 1;
        while (running) {
            try {
                connectAndStream();
                backoffSeconds = 1; // clean end of stream — reconnect promptly
            } catch (Exception e) {
                if (!running) {
                    return;
                }
                log.debug("Delivery stream disconnected ({}); reconnecting in {}s",
                        e.getMessage(), backoffSeconds);
            }
            if (!running) {
                return;
            }
            try {
                Thread.sleep(Duration.ofSeconds(backoffSeconds).toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            backoffSeconds = Math.min(backoffSeconds * 2, MAX_BACKOFF.toSeconds());
        }
    }

    private void connectAndStream() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(properties.getServerUrl() + "/api/agent/deliveries"))
                .header("Accept", "text/event-stream")
                .header("X-API-Key", properties.hasApiKey() ? properties.getApiKey() : "")
                .timeout(Duration.ofDays(365))
                .GET()
                .build();

        HttpResponse<InputStream> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() >= 400) {
            throw new IOException("server returned HTTP " + response.statusCode());
        }

        parseSse(response.body());
    }

    private void parseSse(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String event = null;
            StringBuilder data = new StringBuilder();
            String line;
            while (running && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if ("delivery".equals(event)) {
                        dispatch(data.toString());
                    }
                    event = null;
                    data.setLength(0);
                } else if (line.startsWith("event:")) {
                    event = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) data.append('\n');
                    String payload = line.substring("data:".length());
                    data.append(payload.startsWith(" ") ? payload.substring(1) : payload);
                }
            }
        }
    }

    private void dispatch(String data) {
        try {
            JsonNode node = objectMapper.readTree(data);
            String target = node.path("target").asText("");
            String prompt = node.path("prompt").asText("");
            String result = node.path("result").asText("");
            if (!target.isBlank()) {
                botService.deliverScheduledResult(target, prompt, result);
            }
        } catch (Exception e) {
            log.warn("Could not handle delivery event: {}", e.getMessage());
        }
    }
}
