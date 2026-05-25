package com.example.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** OpenAI-compatible embeddings response; {@code data} entries are ordered by {@code index}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmbeddingResponse(List<Data> data, String model) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(int index, float[] embedding) {}
}
