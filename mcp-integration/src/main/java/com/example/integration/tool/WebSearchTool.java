package com.example.integration.tool;

import com.example.agent.tool.LocalTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
    private static final String ENDPOINT = "https://openrouter.ai/api/v1/chat/completions";
    private static final String SEARCH_MODEL = "perplexity/sonar";

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebSearchTool(@Value("${OPEN_ROUTER_API_KEY:}") String apiKey, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
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
                "model", SEARCH_MODEL,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", "Search the web and provide the latest information about: " + query
                                + "\n\nReturn a concise summary of the most relevant and recent results.")),
                "max_tokens", 1024));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("OpenRouter search API returned {}: {}", response.statusCode(), response.body());
            return "Search failed: OpenRouter API error " + response.statusCode() + ": " + response.body();
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText();
    }
}
