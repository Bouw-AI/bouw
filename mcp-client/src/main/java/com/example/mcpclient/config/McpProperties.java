package com.example.mcpclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mcp")
public record McpProperties(String configFile) {

    public McpProperties {
        if (configFile == null || configFile.isBlank()) {
            configFile = "./mcp-servers.json";
        }
    }

    public String resolvedConfigFile() {
        if (configFile.startsWith("~/")) {
            return System.getProperty("user.home") + configFile.substring(1);
        }
        return configFile;
    }
}
