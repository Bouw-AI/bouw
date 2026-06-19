package com.example.integration.controller;

public record BugReportSummaryResponse(
        String id,
        String title,
        String relativePath,
        String createdAt
) {}
