package com.example.integration.modelsettings;

import java.time.Instant;
import java.util.List;

public record OpenRouterModelRecord(
        String id,
        String name,
        String description,
        Long contextLength,
        String promptPrice,
        String completionPrice,
        List<String> reasoningOptions,
        List<String> supportedParameters,
        Instant updatedAt) {
}
