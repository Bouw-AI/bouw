package com.example.terminal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates GitHub issues via the REST API. Requires {@code GITHUB_TOKEN} in the environment and
 * a repository slug configured via {@code terminal.github-repo}.
 */
@Component
public class GitHubIssueReporter {

    private final String repo;
    private final String githubToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitHubIssueReporter(TerminalProperties properties, ObjectMapper objectMapper) {
        this.repo = properties.githubRepo();
        this.githubToken = System.getenv("GITHUB_TOKEN");
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return githubToken != null && !githubToken.isBlank()
                && repo != null && !repo.isBlank();
    }

    /**
     * Opens a new issue and returns its HTML URL.
     *
     * @throws IOException          on network or API errors
     * @throws InterruptedException if the thread is interrupted while waiting for the response
     */
    public String createIssue(String title, String body) throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("body", body);
        payload.put("labels", List.of("bug"));
        String json = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repo + "/issues"))
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new IOException("GitHub API returned HTTP " + response.statusCode()
                    + ": " + response.body().strip());
        }

        JsonNode node = objectMapper.readTree(response.body());
        return node.path("html_url").asText();
    }
}
