package com.example.agent.tool;

import com.example.agent.scheduler.ScheduledPrompt;
import com.example.agent.scheduler.ScheduledPromptService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Lists the scheduled prompts belonging to the current conversation so the agent can tell the user
 * what is queued and obtain ids to cancel.
 */
@Component
@ConditionalOnProperty(name = "agent.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ListScheduledPromptsTool implements LocalTool {

    private final ScheduledPromptService service;

    public ListScheduledPromptsTool(ScheduledPromptService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "list_scheduled_prompts";
    }

    @Override
    public String description() {
        return "List the prompts currently scheduled for this conversation (the requests you set up "
                + "with schedule_prompt), with their id, schedule, and next run time. Use it to show "
                + "the user what is queued or to find the id of a task to cancel.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        return execute(arguments, new ToolContext(null));
    }

    @Override
    public String execute(Map<String, Object> arguments, ToolContext ctx) {
        String target = ctx == null ? null : ctx.sessionId();
        if (target == null || target.isBlank()) {
            return "No origin session for this request, so no schedules are associated with it.";
        }
        List<ScheduledPrompt> schedules = service.listForTarget(target);
        if (schedules.isEmpty()) {
            return "No scheduled prompts for this conversation.";
        }
        StringBuilder sb = new StringBuilder("Scheduled prompts for this conversation:\n");
        for (ScheduledPrompt sp : schedules) {
            sb.append("- id=").append(sp.id())
                    .append(sp.recurring() ? " [cron " + sp.cron() + "]" : " [once]")
                    .append(" zone=").append(sp.zone())
                    .append(" next=").append(service.describeNextRun(sp)).append('\n')
                    .append("  prompt: ").append(sp.prompt()).append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
