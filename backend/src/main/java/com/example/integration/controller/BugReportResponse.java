package com.example.integration.controller;

import java.util.List;

/**
 * Response for a saved bug report. Only the workspace-relative path and the captured log file
 * labels are returned; absolute host paths are deliberately kept out of the API response to avoid
 * disclosing the server's filesystem layout to clients.
 */
public record BugReportResponse(
        String id,
        String relativePath,
        List<String> logFiles
) {}
