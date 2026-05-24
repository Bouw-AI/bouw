package com.example.agent.model;

/** A tool call requested by the model inside an assistant message. */
public record ToolCall(String id, String type, FunctionCall function) {

    public record FunctionCall(String name, String arguments) {}
}
