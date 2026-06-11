package com.example.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the text-embedding endpoint used by the long-term memory feature.
 *
 * <p>Targets any OpenAI-schema embeddings endpoint ({@code POST {base-url}/embeddings}); defaults to
 * OpenRouter. When {@code api-key} is set it is sent as an {@code Authorization: Bearer} header.
 * {@code model} selects the embedding model specifically (independent of the chat {@code llm.model}).
 */
@ConfigurationProperties("embedding")
public record EmbeddingProperties(String baseUrl, String apiKey, String model) {

    public EmbeddingProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://openrouter.ai/api/v1";
        }
        if (model == null || model.isBlank()) {
            model = "openai/text-embedding-3-small";
        }
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
