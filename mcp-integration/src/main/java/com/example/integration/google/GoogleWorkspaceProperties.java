package com.example.integration.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Google Workspace tools (Docs, Sheets, Drive).
 *
 * <ul>
 *   <li>{@code credentialsFile} — path to a Google <b>service-account</b> JSON key file. This is the
 *       recommended way to authenticate a headless server like Hugin: create a service account in a
 *       Google Cloud project, enable the Docs/Sheets/Drive APIs, download the JSON key, and point this
 *       at it. Defaults to the standard {@code GOOGLE_APPLICATION_CREDENTIALS} environment variable.
 *       When blank/missing, the google_* tools report themselves as unavailable rather than failing
 *       startup.</li>
 *   <li>{@code applicationName} — the application name sent to Google APIs (cosmetic, for quotas/logs).</li>
 *   <li>{@code impersonateUser} — optional. For Google Workspace domains using
 *       <a href="https://developers.google.com/identity/protocols/oauth2/service-account#delegatingauthority">
 *       domain-wide delegation</a>, the email of the user the service account should act as. Leave blank
 *       for standard service-account access (where files must be explicitly shared with the service
 *       account's own email).</li>
 *   <li>{@code defaultShareWith} — optional email address that newly created docs/sheets are
 *       automatically shared with (as a writer). Without this, files created by the service account are
 *       owned by — and only visible to — the service account, so a human can't open them. Per-call
 *       {@code share_with} arguments override this.</li>
 * </ul>
 */
@ConfigurationProperties("google")
public record GoogleWorkspaceProperties(
        String credentialsFile,
        String applicationName,
        String impersonateUser,
        String defaultShareWith) {

    public GoogleWorkspaceProperties {
        if (credentialsFile == null) {
            credentialsFile = "";
        }
        if (applicationName == null || applicationName.isBlank()) {
            applicationName = "Hugin";
        }
        if (impersonateUser == null) {
            impersonateUser = "";
        }
        if (defaultShareWith == null) {
            defaultShareWith = "";
        }
    }
}
