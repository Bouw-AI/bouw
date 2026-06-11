package com.example.integration.google;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small helpers for working with Google document / spreadsheet identifiers. */
public final class GoogleIds {

    // Matches the file id in URLs like https://docs.google.com/document/d/<id>/edit
    // and https://docs.google.com/spreadsheets/d/<id>/edit#gid=0
    private static final Pattern URL_ID = Pattern.compile("/d/([a-zA-Z0-9_-]+)");

    private GoogleIds() {
    }

    /**
     * Accepts either a bare document/spreadsheet id or a full Google URL and returns the id.
     * This lets the model pass whatever the user handed it (often a pasted share link).
     */
    public static String extract(String idOrUrl) {
        if (idOrUrl == null) {
            return null;
        }
        String trimmed = idOrUrl.trim();
        Matcher m = URL_ID.matcher(trimmed);
        if (m.find()) {
            return m.group(1);
        }
        return trimmed;
    }

    public static String docUrl(String documentId) {
        return "https://docs.google.com/document/d/" + documentId + "/edit";
    }

    public static String sheetUrl(String spreadsheetId) {
        return "https://docs.google.com/spreadsheets/d/" + spreadsheetId + "/edit";
    }
}
