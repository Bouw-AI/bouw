package com.example.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** OpenAI-compatible embeddings request ({@code POST {base-url}/embeddings}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmbeddingRequest(String model, List<String> input) {}
