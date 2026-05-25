package com.example.agent;

import com.example.agent.model.ChatMessage;
import com.example.agent.model.ChatRequest;
import com.example.agent.model.ChatResponse;
import com.example.agent.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Thin HTTP wrapper around any OpenAI-schema chat-completions endpoint
 * ({@code POST {base-url}/chat/completions}).
 *
 * <p>The active provider (Ollama, OpenRouter, etc.) and its base URL / API key come from
 * {@link LlmProperties}. When the provider supplies an API key it is sent as an
 * {@code Authorization: Bearer} header; otherwise no auth header is added.
 */
@Component
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    private final RestClient restClient;
    private final URI endpoint;

    public OpenAiClient(LlmProperties properties) {
        LlmProperties.Provider provider = properties.activeProvider();
        String baseUrl = provider.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "llm.providers." + properties.provider() + ".base-url must be set");
        }
        this.endpoint = URI.create(stripTrailingSlash(baseUrl) + "/chat/completions");

        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        // Remote providers (e.g. OpenRouter) can be slower than a local Ollama; the overall
        // agent loop is still bounded by agent.request-timeout.
        factory.setReadTimeout(Duration.ofSeconds(120));

        var builder = RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor(new LoggingInterceptor());

        if (provider.hasApiKey()) {
            builder.requestInterceptor(new BearerAuthInterceptor(provider.apiKey()));
        }

        this.restClient = builder.build();
        log.info("OpenAiClient configured: provider={}, endpoint={}, auth={}",
                properties.provider(), endpoint, provider.hasApiKey() ? "bearer" : "none");
    }

    /**
     * Sends a chat-completions request and returns the parsed response.
     *
     * @param model    model name (provider-specific, e.g. {@code llama3.2} or {@code deepseek/deepseek-chat})
     * @param messages conversation history
     * @param tools    tool definitions to advertise; pass an empty list to omit tool calling
     */
    public ChatResponse chat(String model, List<ChatMessage> messages, List<ToolDefinition> tools) {
        boolean hasTools = tools != null && !tools.isEmpty();
        ChatRequest request = new ChatRequest(
                model,
                messages,
                hasTools ? tools : null,
                hasTools ? "auto" : null,
                false
        );

        log.debug("Sending chat request: model={}, messages={}, tools={}",
                model, messages.size(), hasTools ? tools.size() : 0);

        return restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatResponse.class);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Adds {@code Authorization: Bearer <key>} to every request (OpenRouter, OpenAI, etc.). */
    private record BearerAuthInterceptor(String apiKey) implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            request.getHeaders().setBearerAuth(apiKey);
            return execution.execute(request, body);
        }
    }

    private static class LoggingInterceptor implements ClientHttpRequestInterceptor {

        private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            if (log.isDebugEnabled()) {
                log.debug("→ LLM {} {}\n{}", request.getMethod(), request.getURI(),
                        new String(body, StandardCharsets.UTF_8));
            }
            ClientHttpResponse response = execution.execute(request, body);
            byte[] responseBody = response.getBody().readAllBytes();
            if (log.isDebugEnabled()) {
                log.debug("← LLM {} {}\n{}", request.getMethod(), response.getStatusCode(),
                        new String(responseBody, StandardCharsets.UTF_8));
            }
            return new BufferedClientHttpResponse(response, responseBody);
        }
    }

    private record BufferedClientHttpResponse(ClientHttpResponse delegate, byte[] body)
            implements ClientHttpResponse {

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
