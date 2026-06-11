package com.example.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** OpenAI-compatible chat-completions response. */
public record ChatResponse(String id, List<Choice> choices) {

    public record Choice(
            int index,
            ChatMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {}
}
