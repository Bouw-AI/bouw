package com.example.agent;

import com.example.agent.model.EmbeddingRequest;
import com.example.agent.model.EmbeddingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Thin HTTP wrapper around an OpenAI-schema embeddings endpoint
 * ({@code POST {base-url}/embeddings}), configured from {@link EmbeddingProperties}.
 *
 * <p>Only created when {@code memory.enabled=true}; no network call is made at construction.
 */
@Component
@ConditionalOnProperty(prefix = "memory", name = "enabled", havingValue = "true")
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    private final RestClient restClient;
    private final URI endpoint;
    private final String model;

    public EmbeddingClient(EmbeddingProperties properties) {
        String baseUrl = properties.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("embedding.base-url must be set when memory is enabled");
        }
        this.endpoint = URI.create(stripTrailingSlash(baseUrl) + "/embeddings");
        this.model = properties.model();

        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(60));

        var builder = RestClient.builder().requestFactory(factory);
        if (properties.hasApiKey()) {
            builder.defaultHeaders(headers -> headers.setBearerAuth(properties.apiKey()));
        }
        this.restClient = builder.build();

        log.info("EmbeddingClient configured: endpoint={}, model={}, auth={}",
                endpoint, model, properties.hasApiKey() ? "bearer" : "none");
    }

    /** Embeds a single text, returning its vector. */
    public float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }

    /** Embeds a batch of texts, returning vectors in the same order as {@code inputs}. */
    public List<float[]> embed(List<String> inputs) {
        EmbeddingResponse response = restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbeddingRequest(model, inputs))
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("Embedding response contained no data");
        }
        return response.data().stream()
                .sorted(Comparator.comparingInt(EmbeddingResponse.Data::index))
                .map(EmbeddingResponse.Data::embedding)
                .toList();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
