package com.example.agent.model;

import java.util.List;

/** Final response from the agent including the full conversation history. */
public record AgentResponse(String response, List<ChatMessage> conversationHistory) {}
