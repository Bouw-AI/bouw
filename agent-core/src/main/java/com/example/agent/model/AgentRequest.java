package com.example.agent.model;

/** Request sent to the agent via the REST API. */
public record AgentRequest(String prompt, String model) {

    public AgentRequest {
        if (model == null || model.isBlank()) {
            model = "llama3.2";
        }
    }
}
