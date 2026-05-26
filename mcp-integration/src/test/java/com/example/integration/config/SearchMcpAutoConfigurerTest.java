package com.example.integration.config;

import com.example.mcpclient.model.McpServerDefinition;
import com.example.mcpclient.service.McpServerRegistryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchMcpAutoConfigurerTest {

    @Mock
    McpServerRegistryService registry;

    @Test
    void skipsRegistrationWhenAlreadyConnected() {
        when(registry.isConnected("web-search")).thenReturn(true);
        var props = new SearchProperties("duckduckgo", null);
        var configurer = new SearchMcpAutoConfigurer(registry, props);

        configurer.registerSearchServer();

        verify(registry, never()).connectTransient(any(), any());
    }

    @Test
    void skipsRegistrationForNoneProvider() {
        when(registry.isConnected("web-search")).thenReturn(false);
        var props = new SearchProperties("none", null);
        var configurer = new SearchMcpAutoConfigurer(registry, props);

        configurer.registerSearchServer();

        verify(registry, never()).connectTransient(any(), any());
    }

    @Test
    void registersOpenrouterProvider() {
        when(registry.isConnected("web-search")).thenReturn(false);
        var props = new SearchProperties("openrouter", "./my-search-script.py");
        var configurer = new SearchMcpAutoConfigurer(registry, props);

        configurer.registerSearchServer();

        var captor = ArgumentCaptor.forClass(McpServerDefinition.class);
        verify(registry).connectTransient(eq("web-search"), captor.capture());

        McpServerDefinition def = captor.getValue();
        assertThat(def.command()).isEqualTo("python3");
        assertThat(def.args()).containsExactly("./my-search-script.py");
        assertThat(def.resolvedType()).isEqualTo(McpServerDefinition.ServerType.STDIO);
    }

    @Test
    void registersDefaultDuckduckgoProvider() {
        when(registry.isConnected("web-search")).thenReturn(false);
        var props = new SearchProperties("duckduckgo", null);
        var configurer = new SearchMcpAutoConfigurer(registry, props);

        configurer.registerSearchServer();

        var captor = ArgumentCaptor.forClass(McpServerDefinition.class);
        verify(registry).connectTransient(eq("web-search"), captor.capture());

        McpServerDefinition def = captor.getValue();
        assertThat(def.command()).isEqualTo("uvx");
        assertThat(def.args()).containsExactly("duckduckgo-mcp-server");
    }

    @Test
    void registersUnknownProviderAsDuckduckgoFallback() {
        when(registry.isConnected("web-search")).thenReturn(false);
        var props = new SearchProperties("some-unknown-provider", null);
        var configurer = new SearchMcpAutoConfigurer(registry, props);

        configurer.registerSearchServer();

        var captor = ArgumentCaptor.forClass(McpServerDefinition.class);
        verify(registry).connectTransient(eq("web-search"), captor.capture());

        McpServerDefinition def = captor.getValue();
        assertThat(def.command()).isEqualTo("uvx");
        assertThat(def.args()).containsExactly("duckduckgo-mcp-server");
    }

    @Test
    void openrouterUsesDefaultScriptWhenNoneProvided() {
        when(registry.isConnected("web-search")).thenReturn(false);
        // SearchProperties compact constructor sets the default script when null/blank
        var props = new SearchProperties("openrouter", null);
        var configurer = new SearchMcpAutoConfigurer(registry, props);

        configurer.registerSearchServer();

        var captor = ArgumentCaptor.forClass(McpServerDefinition.class);
        verify(registry).connectTransient(eq("web-search"), captor.capture());

        McpServerDefinition def = captor.getValue();
        assertThat(def.command()).isEqualTo("python3");
        assertThat(def.args()).containsExactly("./openrouter-search-mcp.py");
    }
}
