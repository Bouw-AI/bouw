package com.example.integration.google;

/** Response from the reconnect endpoint. */
public record GoogleReconnectResponse(
        GoogleWorkspaceStatus status,
        String authUrl
) {}
