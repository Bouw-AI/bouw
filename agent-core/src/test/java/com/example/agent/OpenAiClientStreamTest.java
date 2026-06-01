package com.example.agent;

import com.example.agent.model.ChatMessage;
import com.example.agent.model.ChatResponse;
import com.example.agent.model.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link OpenAiClient#chatStream} against a local HTTP server that returns canned
 * Server-Sent Events: content is streamed in order and tool-call fragments are reassembled.
 */
class OpenAiClientStreamTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void streamsContentTokensInOrderAndAssemblesMessage() throws IOException {
        String sse = """
                data: {"choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}

                data: {"choices":[{"index":0,"delta":{"content":", "},"finish_reason":null}]}

                data: {"choices":[{"index":0,"delta":{"content":"world"},"finish_reason":null}]}

                data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """;
        OpenAiClient client = clientServing(sse);

        List<String> tokens = new ArrayList<>();
        ChatResponse response = client.chatStream(
                "m", List.of(ChatMessage.user("hi")), List.of(), tokens::add);

        assertThat(tokens).containsExactly("Hello", ", ", "world");
        ChatResponse.Choice choice = response.choices().get(0);
        assertThat(choice.message().content()).isEqualTo("Hello, world");
        assertThat(choice.message().toolCalls()).isNull();
        assertThat(choice.finishReason()).isEqualTo("stop");
    }

    @Test
    void reassemblesToolCallFromStreamedFragments() throws IOException {
        String sse = """
                data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"get_time","arguments":""}}]},"finish_reason":null}]}

                data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\\"tz\\":"}}]},"finish_reason":null}]}

                data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\\"UTC\\"}"}}]},"finish_reason":null}]}

                data: {"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

                data: [DONE]

                """;
        OpenAiClient client = clientServing(sse);

        List<String> tokens = new ArrayList<>();
        ChatResponse response = client.chatStream(
                "m", List.of(ChatMessage.user("time?")), List.of(), tokens::add);

        assertThat(tokens).isEmpty();
        ChatMessage message = response.choices().get(0).message();
        assertThat(message.content()).isNull();
        assertThat(message.toolCalls()).hasSize(1);
        ToolCall call = message.toolCalls().get(0);
        assertThat(call.id()).isEqualTo("call_1");
        assertThat(call.function().name()).isEqualTo("get_time");
        assertThat(call.function().arguments()).isEqualTo("{\"tz\":\"UTC\"}");
        assertThat(response.choices().get(0).finishReason()).isEqualTo("tool_calls");
    }

    @Test
    void fallsBackToReasoningWhenContentEmpty() throws IOException {
        // A reasoning model (e.g. gpt-oss / deepseek) that only ever emits "reasoning" deltas and
        // no content — this previously produced an empty assistant message ("The agent finished
        // without producing a text answer."). The reasoning text is used as a content fallback.
        String sse = """
                data: {"choices":[{"index":0,"delta":{"reasoning":"Let me think. "},"finish_reason":null}]}

                data: {"choices":[{"index":0,"delta":{"reasoning":"The answer is 42."},"finish_reason":null}]}

                data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """;
        OpenAiClient client = clientServing(sse);

        List<String> tokens = new ArrayList<>();
        ChatResponse response = client.chatStream(
                "m", List.of(ChatMessage.user("hi")), List.of(), tokens::add);

        // Reasoning is not streamed live, but is surfaced as the final content fallback.
        assertThat(tokens).isEmpty();
        assertThat(response.choices().get(0).message().content())
                .isEqualTo("Let me think. The answer is 42.");
    }

    @Test
    void prefersContentOverReasoningWhenBothPresent() throws IOException {
        String sse = """
                data: {"choices":[{"index":0,"delta":{"reasoning":"thinking..."},"finish_reason":null}]}

                data: {"choices":[{"index":0,"delta":{"content":"Final answer."},"finish_reason":null}]}

                data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """;
        OpenAiClient client = clientServing(sse);

        List<String> tokens = new ArrayList<>();
        ChatResponse response = client.chatStream(
                "m", List.of(ChatMessage.user("hi")), List.of(), tokens::add);

        // Only real content is surfaced; reasoning is ignored when content exists.
        assertThat(tokens).containsExactly("Final answer.");
        assertThat(response.choices().get(0).message().content()).isEqualTo("Final answer.");
    }

    private OpenAiClient clientServing(String sseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = sseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        int port = server.getAddress().getPort();
        var properties = new LlmProperties("test", "m",
                Map.of("test", new LlmProperties.Provider("http://localhost:" + port + "/v1", null)));
        return new OpenAiClient(properties, new ObjectMapper());
    }
}
