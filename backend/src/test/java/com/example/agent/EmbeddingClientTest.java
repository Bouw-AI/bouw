package com.example.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link EmbeddingClient} against a local HTTP server returning a canned OpenAI-schema
 * embeddings response: vectors are parsed and returned in {@code index} order.
 */
class EmbeddingClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesEmbeddingVectors() throws IOException {
        String json = """
                {"model":"test-embed","data":[
                  {"index":0,"embedding":[0.1,0.2,0.3]},
                  {"index":1,"embedding":[0.4,0.5,0.6]}
                ]}""";
        AtomicReference<String> requestBody = new AtomicReference<>();
        EmbeddingClient client = clientServing(json, requestBody);

        List<float[]> vectors = client.embed(List.of("hello", "world"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(vectors.get(1)).containsExactly(0.4f, 0.5f, 0.6f);
        assertThat(requestBody.get()).contains("\"model\":\"test-embed\"");
        assertThat(requestBody.get()).contains("\"input\":[\"hello\",\"world\"]");
    }

    @Test
    void embedSingleReturnsFirstVector() throws IOException {
        String json = """
                {"model":"test-embed","data":[{"index":0,"embedding":[1.0,2.0]}]}""";
        EmbeddingClient client = clientServing(json, new AtomicReference<>());

        assertThat(client.embed("hi")).containsExactly(1.0f, 2.0f);
    }

    @Test
    void reordersOutOfOrderData() throws IOException {
        String json = """
                {"data":[
                  {"index":1,"embedding":[2.0]},
                  {"index":0,"embedding":[1.0]}
                ]}""";
        EmbeddingClient client = clientServing(json, new AtomicReference<>());

        List<float[]> vectors = client.embed(List.of("a", "b"));

        assertThat(vectors.get(0)).containsExactly(1.0f);
        assertThat(vectors.get(1)).containsExactly(2.0f);
    }

    private EmbeddingClient clientServing(String responseBody, AtomicReference<String> capturedRequest)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            capturedRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        int port = server.getAddress().getPort();
        var properties = new EmbeddingProperties(
                "http://localhost:" + port + "/v1", null, "test-embed");
        return new EmbeddingClient(properties);
    }
}
