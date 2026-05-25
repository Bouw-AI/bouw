package com.example.agent;

/** Small vector helpers for similarity search over embeddings. */
public final class Vectors {

    private Vectors() {
    }

    /**
     * Cosine similarity of two equal-length vectors, in {@code [-1, 1]}. Returns {@code 0} for null,
     * empty, mismatched-length, or zero-magnitude inputs (no similarity can be defined).
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
