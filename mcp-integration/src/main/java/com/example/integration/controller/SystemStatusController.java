package com.example.integration.controller;

import com.example.mcpclient.service.McpServerRegistryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/status")
public class SystemStatusController {

    private final McpServerRegistryService registry;
    private final String llmProvider;
    private final String llmModel;
    private final boolean memoryEnabled;
    private final String memoryKeyPrefix;
    private final int memoryTopK;

    public SystemStatusController(
            McpServerRegistryService registry,
            @Value("${llm.provider}") String llmProvider,
            @Value("${llm.model}") String llmModel,
            @Value("${memory.enabled:false}") boolean memoryEnabled,
            @Value("${memory.key-prefix:agent:memory}") String memoryKeyPrefix,
            @Value("${memory.top-k:3}") int memoryTopK) {
        this.registry = registry;
        this.llmProvider = llmProvider;
        this.llmModel = llmModel;
        this.memoryEnabled = memoryEnabled;
        this.memoryKeyPrefix = memoryKeyPrefix;
        this.memoryTopK = memoryTopK;
    }

    @GetMapping
    public List<ServiceStatus> getStatus() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        var list = new ArrayList<ServiceStatus>();

        list.add(new ServiceStatus("agent", "Agent Server",
                "localhost:8080 · /actuator/health", "up", formatUptime(uptimeMs)));

        list.add(new ServiceStatus("llm", "LLM Provider",
                llmProvider + " · " + llmModel, "up", null));

        list.add(new ServiceStatus("memory", "Long-term Memory",
                memoryEnabled
                        ? "Redis · " + memoryKeyPrefix + " · top-k " + memoryTopK
                        : "disabled · short-term memory active",
                memoryEnabled ? "up" : "down", null));

        registry.listServers().stream()
                .filter(s -> s.name().toLowerCase().contains("search"))
                .findFirst()
                .ifPresentOrElse(
                        ws -> list.add(new ServiceStatus("search", "Web Search MCP",
                                ws.definition() != null
                                        ? (ws.definition().url() != null ? ws.definition().url() : ws.definition().command())
                                        : ws.name(),
                                ws.connected() ? "up" : "error", null)),
                        () -> list.add(new ServiceStatus("search", "Web Search MCP",
                                "not configured", "down", null)));

        return list;
    }

    private String formatUptime(long ms) {
        long s = ms / 1000, m = s / 60, h = m / 60, d = h / 24;
        if (d > 0) return "uptime " + d + "d " + (h % 24) + "h";
        if (h > 0) return "uptime " + h + "h " + (m % 60) + "m";
        return "uptime " + m + "m";
    }

    public record ServiceStatus(String key, String name, String detail, String status, String meta) {}
}
