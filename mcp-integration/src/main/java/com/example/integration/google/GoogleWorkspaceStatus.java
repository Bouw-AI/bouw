package com.example.integration.google;

/**
 * Snapshot of the Google Workspace integration for the UI.
 *
 * <p>{@code active} means the Docs/Sheets/Calendar/Gmail tools are ready to use right now.
 * {@code reconnectable} means the backend can try to re-run the consent flow from the sidebar.
 */
public record GoogleWorkspaceStatus(
        boolean active,
        boolean configured,
        boolean reconnectable,
        String authMode,
        String message
) {}
