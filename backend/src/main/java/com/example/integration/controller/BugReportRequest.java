package com.example.integration.controller;

import com.fasterxml.jackson.databind.JsonNode;

public record BugReportRequest(
        String sessionId,
        String title,
        String sandboxId,
        String agentId,
        JsonNode thread,
        JsonNode clientContext
) {}
