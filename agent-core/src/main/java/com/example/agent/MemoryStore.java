package com.example.agent;

import com.example.agent.model.MemoryRecord;

import java.util.List;

/**
 * Storage backend for long-term memory. Kept in {@code agent-core} as an interface so the agent
 * logic stays decoupled from the concrete store (mirrors the {@link McpToolProvider} boundary); the
 * Redis implementation lives in {@code mcp-integration}.
 */
public interface MemoryStore {

    /** Persists a memory record. */
    void save(MemoryRecord record);

    /**
     * Returns up to {@code topK} stored memories most similar to {@code queryEmbedding}, ordered by
     * descending similarity, excluding any below {@code minScore}.
     */
    List<ScoredMemory> search(float[] queryEmbedding, int topK, double minScore);

    /** A stored memory paired with its similarity to a query. */
    record ScoredMemory(MemoryRecord record, double score) {}
}
