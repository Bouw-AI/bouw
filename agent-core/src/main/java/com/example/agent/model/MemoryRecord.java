package com.example.agent.model;

import java.time.Instant;

/** A single stored long-term memory: the recalled text plus its embedding vector. */
public record MemoryRecord(String id, String text, float[] embedding, Instant createdAt) {}
