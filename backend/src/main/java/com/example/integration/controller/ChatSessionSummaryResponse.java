package com.example.integration.controller;

import java.time.Instant;

public record ChatSessionSummaryResponse(
        String id,
        String title,
        String mode,
        Instant createdAt,
        Instant updatedAt
) {}