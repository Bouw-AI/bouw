package com.example.agent;

import com.example.agent.prompts.Prompts;

import java.util.Map;

/** Point-in-time snapshot of the machine's capabilities. */
public record SystemFacts(
        String osName,
        String osVersion,
        String arch,
        int availableProcessors,
        long totalMemoryBytes,
        long freeMemoryBytes,
        long maxHeapBytes,
        long freeDiskBytes,
        String javaVersion,
        Map<String, Boolean> toolchains) {

    /** Compact multi-line summary injected as a system message on every agent request. */
    public String summary() {
        return Prompts.systemFacts(this);
    }
}
