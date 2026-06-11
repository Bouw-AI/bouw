package com.example.integration.controller;

import com.example.integration.google.GoogleWorkspaceClientFactory;
import com.example.integration.google.GoogleReconnectRequest;
import com.example.integration.google.GoogleReconnectResponse;
import com.example.integration.google.GoogleWorkspaceStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for checking and reconnecting the Google Workspace integration. */
@RestController
@RequestMapping("/api/google")
public class GoogleWorkspaceController {

    private final GoogleWorkspaceClientFactory google;

    public GoogleWorkspaceController(GoogleWorkspaceClientFactory google) {
        this.google = google;
    }

    @GetMapping("/status")
    public GoogleWorkspaceStatus status() {
        return google.status();
    }

    @PostMapping("/reconnect")
    public GoogleReconnectResponse reconnect(@RequestBody(required = false) GoogleReconnectRequest request) throws Exception {
        return google.beginReconnect(request == null ? null : request.returnTo());
    }
}
