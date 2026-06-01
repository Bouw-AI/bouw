package com.example.integration.tool;

import com.example.agent.tool.LocalTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class WebSearchTool implements LocalTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BASE_MS = 1_000;

    private final String apiKey;
    private final String endpoint;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public WebSearchTool(
            @Value("${OPEN_ROUTER_API_KEY:}") String apiKey,
            @Value("${web.search.endpoint:https://openrouter.ai/api/v1/chat/completions}") String endpoint,
            @Value("${web.search.model:perplexity/sonar}") String model,
            ObjectMapper objectMapper) {
        this(apiKey, endpoint, model, objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    WebSearchTool(String apiKey, String endpoint, String model, ObjectMapper objectMapper, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.model = model;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String description() {
        return "Search the web for current information, news, and recent events. "
                + "Returns a summary of the most relevant and up-to-date search results.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "The search query")),
                "required", List.of("query"));
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            return "web_search is unavailable: OPEN_ROUTER_API_KEY is not set.";
        }

        String query = requiredString(arguments, "query");
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", "Search the web and provide the latest information about: " + query
                                + "\n\nReturn a concise summary of the most relevant and recent results.")),
                "max_tokens", 1024));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        IOException lastNetworkError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode content = root.path("choices").path(0).path("message").path("content");
                    if (content.isMissingNode() || content.isNull()) {
                        log.warn("OpenRouter search: missing content field in 200 response: {}", response.body());
                        return "Search failed: unexpected response structure (missing content field)";
                    }
                    return content.asText();
                }

                if ((status == 429 || status >= 500) && attempt < MAX_ATTEMPTS) {
                    long delay = RETRY_BASE_MS * (1L << (attempt - 1));
                    log.warn("OpenRouter search returned {} on attempt {}; retrying in {}ms", status, attempt, delay);
                    Thread.sleep(delay);
                    continue;
                }

                log.warn("OpenRouter search API returned {}: {}", status, response.body());
                return "Search failed: OpenRouter API error " + status + ": " + response.body();

            } catch (IOException e) {
                lastNetworkError = e;
                if (attempt < MAX_ATTEMPTS) {
                    long delay = RETRY_BASE_MS * (1L << (attempt - 1));
                    log.warn("OpenRouter search network error on attempt {}; retrying in {}ms: {}", attempt, delay, e.getMessage());
                    Thread.sleep(delay);
                }
            }
        }

        throw new IOException("OpenRouter search failed after " + MAX_ATTEMPTS + " attempts", lastNetworkError);
    }
}
