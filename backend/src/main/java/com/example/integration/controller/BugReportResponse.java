package com.example.integration.controller;

import java.util.List;

public record BugReportResponse(
        String relativePath,
        String absolutePath,
        String workspaceRoot,
        List<String> logFiles
) {}
