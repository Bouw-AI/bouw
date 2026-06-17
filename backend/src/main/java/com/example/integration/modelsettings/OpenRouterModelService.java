package com.example.integration.modelsettings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class OpenRouterModelService {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterModelService.class);
    private static final List<String> REASONING_EFFORT_OPTIONS =
            List.of("none", "minimal", "low", "medium", "high", "xhigh");
    private static final Duration SYNC_TTL = Duration.ofHours(6);

    private final OpenRouterModelRepository modelRepository;
    private final UserModelPreferenceRepository preferenceRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Clock clock;
    private final String modelsEndpoint;
    private final String apiKey;
    private final String defaultModel;

    @Autowired
    public OpenRouterModelService(
            OpenRouterModelRepository modelRepository,
            UserModelPreferenceRepository preferenceRepository,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${openrouter.models.endpoint:https://openrouter.ai/api/v1/models}") String modelsEndpoint,
            @Value("${OPEN_ROUTER_API_KEY:}") String apiKey,
            @Value("${llm.model:}") String defaultModel) {
        this(modelRepository, preferenceRepository, objectMapper, clock, modelsEndpoint, apiKey, defaultModel,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    OpenRouterModelService(
            OpenRouterModelRepository modelRepository,
            UserModelPreferenceRepository preferenceRepository,
            ObjectMapper objectMapper,
            Clock clock,
            String modelsEndpoint,
            String apiKey,
            String defaultModel,
            HttpClient httpClient) {
        this.modelRepository = modelRepository;
        this.preferenceRepository = preferenceRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.modelsEndpoint = modelsEndpoint;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.httpClient = httpClient;
    }

    @Transactional
    public ModelSettingsResponse listModels(String ownerUsername, boolean enabledOnly) {
        syncIfStale();
        List<OpenRouterModelRecord> catalog = modelRepository.findAll();
        Set<String> enabledIds = resolveEnabledIds(ownerUsername, catalog);
        List<ModelSettingsResponse.ModelOption> models = new ArrayList<>();
        for (OpenRouterModelRecord model : catalog) {
            boolean enabled = enabledIds.contains(model.id());
            if (enabledOnly && !enabled) {
                continue;
            }
            models.add(new ModelSettingsResponse.ModelOption(
                    model.id(),
                    model.name(),
                    model.description(),
                    model.contextLength(),
                    model.promptPrice(),
                    model.completionPrice(),
                    model.reasoningOptions(),
                    enabled));
        }
        return new ModelSettingsResponse(models);
    }

    @Transactional
    public ModelSettingsResponse updateEnabledModels(String ownerUsername, List<String> enabledModelIds) {
        syncIfStale();
        List<OpenRouterModelRecord> catalog = modelRepository.findAll();
        Set<String> validIds = new LinkedHashSet<>();
        for (OpenRouterModelRecord model : catalog) {
            validIds.add(model.id());
        }
        Set<String> enabled = new LinkedHashSet<>();
        if (enabledModelIds != null) {
            for (String id : enabledModelIds) {
                if (id != null && validIds.contains(id)) {
                    enabled.add(id);
                }
            }
        }
        Instant now = Instant.now(clock);
        for (String modelId : validIds) {
            preferenceRepository.save(ownerUsername, modelId, enabled.contains(modelId), now);
        }
        return listModels(ownerUsername, false);
    }

    private void syncIfStale() {
        Instant latest = modelRepository.latestUpdate();
        if (latest != null && latest.plus(SYNC_TTL).isAfter(Instant.now(clock))) {
            return;
        }
        syncNow();
    }

    @Transactional
    void syncNow() {
        try {
            List<OpenRouterModelRecord> fetched = fetchModels();
            Set<String> ids = new LinkedHashSet<>();
            for (OpenRouterModelRecord record : fetched) {
                modelRepository.upsert(record);
                ids.add(record.id());
            }
            modelRepository.deleteMissing(ids);
        } catch (Exception e) {
            if (modelRepository.findAll().isEmpty()) {
                throw new IllegalStateException("Could not load models from OpenRouter: " + e.getMessage(), e);
            }
            log.warn("Could not refresh OpenRouter model catalog: {}", e.getMessage());
        }
    }

    List<OpenRouterModelRecord> fetchModels() throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(modelsEndpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("OpenRouter models returned HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        List<OpenRouterModelRecord> records = new ArrayList<>();
        Instant now = Instant.now(clock);
        for (JsonNode item : root.path("data")) {
            if (!supportsChatTools(item)) {
                continue;
            }
            String id = item.path("id").asText("");
            if (id.isBlank()) {
                continue;
            }
            records.add(new OpenRouterModelRecord(
                    id,
                    item.path("name").asText(id),
                    blankToNull(item.path("description").asText(null)),
                    item.path("context_length").isNumber() ? item.path("context_length").longValue() : null,
                    normalizePrice(item.path("pricing").path("prompt").asText(null)),
                    normalizePrice(item.path("pricing").path("completion").asText(null)),
                    reasoningOptions(item),
                    supportedParameters(item),
                    now));
        }
        return records;
    }

    private Set<String> resolveEnabledIds(String ownerUsername, List<OpenRouterModelRecord> catalog) {
        Set<String> enabled = preferenceRepository.findEnabledModelIds(ownerUsername);
        if (preferenceRepository.countRowsForOwner(ownerUsername) > 0) {
            return enabled;
        }
        String fallback = defaultModel;
        if (fallback != null && catalog.stream().anyMatch(model -> model.id().equals(fallback))) {
            return Set.of(fallback);
        }
        return catalog.isEmpty() ? Set.of() : Set.of(catalog.get(0).id());
    }

    private static boolean supportsChatTools(JsonNode item) {
        JsonNode architecture = item.path("architecture");
        if (!architecture.path("output_modalities").isArray()) {
            return false;
        }
        boolean outputsText = false;
        for (JsonNode modality : architecture.path("output_modalities")) {
            if ("text".equalsIgnoreCase(modality.asText())) {
                outputsText = true;
                break;
            }
        }
        if (!outputsText) {
            return false;
        }
        Set<String> parameters = supportedParameters(item).stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return parameters.contains("tools");
    }

    private static List<String> supportedParameters(JsonNode item) {
        List<String> supported = new ArrayList<>();
        for (JsonNode parameter : item.path("supported_parameters")) {
            String value = parameter.asText("");
            if (!value.isBlank()) {
                supported.add(value);
            }
        }
        return supported;
    }

    private static List<String> reasoningOptions(JsonNode item) {
        Set<String> supported = supportedParameters(item).stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (supported.contains("reasoning") || supported.contains("reasoning_effort")) {
            return REASONING_EFFORT_OPTIONS;
        }
        return List.of();
    }

    private static String normalizePrice(String raw) {
        if (raw == null || raw.isBlank() || "-1".equals(raw)) {
            return null;
        }
        try {
            double unitPrice = Double.parseDouble(raw);
            double perMillion = unitPrice * 1_000_000d;
            return String.format(Locale.US, "%.4f", perMillion);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
