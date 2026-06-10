package com.example.agent.tool;

import com.example.agent.scheduler.ScheduledPrompt;
import com.example.agent.scheduler.ScheduledPromptService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Cancels a previously scheduled prompt by id. Cancellation is scoped to the current conversation:
 * the id must belong to this origin session, so one conversation cannot cancel another's schedules.
 */
@Component
@ConditionalOnProperty(name = "agent.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class CancelScheduledPromptTool implements LocalTool {

    private final ScheduledPromptService service;

    public CancelScheduledPromptTool(ScheduledPromptService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "cancel_scheduled_prompt";
    }

    @Override
    public String description() {
        return "Cancel a scheduled prompt by its id (as shown by list_scheduled_prompts). Only "
                + "schedules created in this conversation can be cancelled.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "id", Map.of(
                                "type", "string",
                                "description", "The id of the scheduled prompt to cancel.")),
                "required", List.of("id"));
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        return execute(arguments, new ToolContext(null));
    }

    @Override
    public String execute(Map<String, Object> arguments, ToolContext ctx) {
        String id = requiredString(arguments, "id");
        String target = ctx == null ? null : ctx.sessionId();

        // Scope cancellation to the caller's own schedules.
        List<ScheduledPrompt> own = target == null ? List.of() : service.listForTarget(target);
        boolean ownsIt = own.stream().anyMatch(p -> p.id().equals(id));
        if (!ownsIt) {
            return "No scheduled prompt with id '" + id + "' belongs to this conversation.";
        }

        boolean cancelled = service.cancel(id);
        return cancelled
                ? "Cancelled scheduled prompt " + id + "."
                : "No scheduled prompt with id '" + id + "' was found.";
    }
}
