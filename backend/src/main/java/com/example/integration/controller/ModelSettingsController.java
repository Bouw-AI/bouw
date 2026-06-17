package com.example.integration.controller;

import com.example.integration.modelsettings.ModelSettingsResponse;
import com.example.integration.modelsettings.OpenRouterModelService;
import com.example.integration.modelsettings.UpdateModelSettingsRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/models")
public class ModelSettingsController {

    private final OpenRouterModelService modelService;

    public ModelSettingsController(OpenRouterModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    public ModelSettingsResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean enabledOnly) {
        return modelService.listModels(owner(jwt), enabledOnly);
    }

    @PutMapping("/preferences")
    public ModelSettingsResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpdateModelSettingsRequest request) {
        return modelService.updateEnabledModels(owner(jwt), request.enabledModelIds());
    }

    private static String owner(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return jwt.getSubject();
    }
}
