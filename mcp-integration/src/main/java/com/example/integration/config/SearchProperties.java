package com.example.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the web-search MCP server that is registered automatically at startup.
 *
 * <p>Set {@code search.provider} to {@code openrouter} to use the Perplexity-backed search
 * (works on cloud IPs, requires {@code OPEN_ROUTER_API_KEY}) or {@code duckduckgo} to use
 * the DuckDuckGo MCP server (no API key needed, but may fail with bot detection on server IPs).
 */
@ConfigurationProperties("search")
public record SearchProperties(String provider, String openrouterScript) {

    public SearchProperties {
        if (provider == null || provider.isBlank()) provider = "duckduckgo";
        if (openrouterScript == null || openrouterScript.isBlank()) openrouterScript = "./openrouter-search-mcp.py";
    }
}
