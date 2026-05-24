package com.example.agent;

import com.example.agent.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core agent loop.
 *
 * <ol>
 *   <li>Fetches all tools from every connected MCP server.</li>
 *   <li>Sends the user prompt to Ollama with those tools advertised.</li>
 *   <li>When the model requests a tool call, routes it to the correct MCP server.</li>
 *   <li>Feeds the tool result back and repeats until the model produces a final answer.</li>
 * </ol>
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final int MAX_ITERATIONS = 10;

    private final OllamaClient ollamaClient;
    private final McpToolProvider toolProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentService(OllamaClient ollamaClient, McpToolProvider toolProvider) {
        this.ollamaClient = ollamaClient;
        this.toolProvider = toolProvider;
    }

    public AgentResponse chat(AgentRequest request) {
        // Build flattened tool list and a reverse lookup: tool name → server name
        Map<String, String> toolServerMap = new LinkedHashMap<>();
        List<ToolDefinition> toolDefinitions = new ArrayList<>();

        toolProvider.getAllToolsByServer().forEach((serverName, tools) ->
                tools.forEach(tool -> {
                    toolServerMap.put(tool.name(), serverName);
                    toolDefinitions.add(ToolDefinition.from(tool));
                })
        );

        log.debug("Agent chat: model={}, tools available={}", request.model(), toolDefinitions.size());

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user(request.prompt()));

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            ChatResponse response = ollamaClient.chat(request.model(), messages, toolDefinitions);
            ChatResponse.Choice choice = response.choices().get(0);
            ChatMessage assistantMsg = choice.message();
            messages.add(assistantMsg);

            String finishReason = choice.finishReason();
            log.debug("Iteration {}: finish_reason={}", i, finishReason);

            if ("tool_calls".equals(finishReason) && assistantMsg.toolCalls() != null) {
                for (ToolCall toolCall : assistantMsg.toolCalls()) {
                    String toolResult = executeToolCall(toolCall, toolServerMap);
                    messages.add(ChatMessage.tool(toolCall.id(), toolResult));
                }
            } else {
                return new AgentResponse(assistantMsg.content(), Collections.unmodifiableList(messages));
            }
        }

        log.warn("Agent reached max iterations ({}) without a final answer", MAX_ITERATIONS);
        return new AgentResponse(
                "Reached the maximum number of tool-call iterations without a final answer.",
                Collections.unmodifiableList(messages));
    }

    private String executeToolCall(ToolCall toolCall, Map<String, String> toolServerMap) {
        String toolName = toolCall.function().name();
        String serverName = toolServerMap.get(toolName);

        if (serverName == null) {
            String msg = "Tool '" + toolName + "' is not available on any connected MCP server.";
            log.warn(msg);
            return msg;
        }

        Map<String, Object> args = parseArguments(toolCall.function().arguments());
        log.debug("Calling tool '{}' on server '{}' with args: {}", toolName, serverName, args);

        try {
            return toolProvider.callTool(serverName, toolName, args);
        } catch (Exception e) {
            String msg = "Tool call failed: " + e.getMessage();
            log.error(msg, e);
            return msg;
        }
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Could not parse tool arguments '{}': {}", arguments, e.getMessage());
            return Map.of();
        }
    }
}
