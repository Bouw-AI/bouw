package com.example.integration.modelsettings;

import java.util.List;

public record ModelSettingsResponse(List<ModelOption> models) {

    public record ModelOption(
            String id,
            String name,
            String description,
            Long contextLength,
            String promptPrice,
            String completionPrice,
            List<String> reasoningOptions,
            boolean enabled) {
    }
}
