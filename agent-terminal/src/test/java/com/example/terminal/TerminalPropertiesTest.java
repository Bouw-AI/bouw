package com.example.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link TerminalProperties} record — canonical constructor
 * normalisation and {@link TerminalProperties#hasApiKey()}.
 */
class TerminalPropertiesTest {

    @Test
    void defaultsToLocalhostWhenUrlIsNull() {
        var props = new TerminalProperties(null, null, null, null);
        assertThat(props.serverUrl()).isEqualTo("http://localhost:8080");
    }

    @Test
    void defaultsToLocalhostWhenUrlIsBlank() {
        var props = new TerminalProperties("   ", null, null, null);
        assertThat(props.serverUrl()).isEqualTo("http://localhost:8080");
    }

    @Test
    void stripsTrailingSlash() {
        var props = new TerminalProperties("http://localhost:8080/", null, null, null);
        assertThat(props.serverUrl()).isEqualTo("http://localhost:8080");
    }

    @Test
    void stripsMultipleTrailingSlashes() {
        var props = new TerminalProperties("http://localhost:8080///", null, null, null);
        assertThat(props.serverUrl()).isEqualTo("http://localhost:8080");
    }

    @Test
    void hasApiKeyReturnsTrueWhenKeyPresent() {
        var props = new TerminalProperties("http://localhost:8080", "secret-key", null, null);
        assertThat(props.hasApiKey()).isTrue();
    }

    @Test
    void hasApiKeyReturnsFalseWhenKeyBlank() {
        var props = new TerminalProperties("http://localhost:8080", "   ", null, null);
        assertThat(props.hasApiKey()).isFalse();
    }

    @Test
    void hasApiKeyReturnsFalseWhenKeyNull() {
        var props = new TerminalProperties("http://localhost:8080", null, null, null);
        assertThat(props.hasApiKey()).isFalse();
    }

    @Test
    void preservesUrlWithPath() {
        var props = new TerminalProperties("http://localhost:8080/api", null, null, null);
        assertThat(props.serverUrl()).isEqualTo("http://localhost:8080/api");
    }

    @Test
    void preservesModelWhenSet() {
        var props = new TerminalProperties("http://localhost:8080", null, "gpt-4", null);
        assertThat(props.model()).isEqualTo("gpt-4");
    }

    @Test
    void modelCanBeNull() {
        var props = new TerminalProperties("http://localhost:8080", null, null, null);
        assertThat(props.model()).isNull();
    }

    @Test
    void githubRepoIsPreserved() {
        var props = new TerminalProperties("http://localhost:8080", null, null, "owner/repo");
        assertThat(props.githubRepo()).isEqualTo("owner/repo");
    }
}
