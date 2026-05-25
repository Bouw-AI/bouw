package com.example.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VectorsTest {

    @Test
    void identicalVectorsHaveSimilarityOne() {
        float[] v = {1f, 2f, 3f};
        assertThat(Vectors.cosineSimilarity(v, v)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void orthogonalVectorsHaveSimilarityZero() {
        assertThat(Vectors.cosineSimilarity(new float[]{1f, 0f}, new float[]{0f, 1f}))
                .isCloseTo(0.0, within(1e-9));
    }

    @Test
    void oppositeVectorsHaveSimilarityNegativeOne() {
        assertThat(Vectors.cosineSimilarity(new float[]{1f, 1f}, new float[]{-1f, -1f}))
                .isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void scaleInvariant() {
        double a = Vectors.cosineSimilarity(new float[]{1f, 2f}, new float[]{2f, 1f});
        double b = Vectors.cosineSimilarity(new float[]{10f, 20f}, new float[]{2f, 1f});
        assertThat(a).isCloseTo(b, within(1e-9));
    }

    @Test
    void degenerateInputsReturnZero() {
        assertThat(Vectors.cosineSimilarity(null, new float[]{1f})).isZero();
        assertThat(Vectors.cosineSimilarity(new float[]{1f}, new float[]{1f, 2f})).isZero();
        assertThat(Vectors.cosineSimilarity(new float[]{0f, 0f}, new float[]{1f, 1f})).isZero();
        assertThat(Vectors.cosineSimilarity(new float[]{}, new float[]{})).isZero();
    }
}
