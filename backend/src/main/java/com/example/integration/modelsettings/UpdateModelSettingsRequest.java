package com.example.integration.modelsettings;

import java.util.List;

public record UpdateModelSettingsRequest(List<String> enabledModelIds) {
}
