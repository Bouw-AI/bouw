package com.example.agent.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Lists the Discord apps (integrations) and slash commands available in the channel that the current
 * conversation originated from. Like {@link ReadDiscordChannelTool}, the data is gathered by the
 * Discord front-end (which has the gateway connection) and threaded through
 * {@link ToolContext#channelApps()} on the request — {@code agent-core} never talks to Discord
 * directly. For non-Discord callers no apps snapshot is present and the tool reports that.
 */
@Component
public class ListDiscordCommandsTool implements LocalTool {

    @Override
    public String name() {
        return "list_discord_commands";
    }

    @Override
    public String description() {
        return "List the Discord apps (integrations) and slash commands available in the channel that "
                + "this conversation came from. Use this to tell the user which bots/apps are present "
                + "and what slash commands they can run here. Only works for conversations that "
                + "originated in a Discord guild channel.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of());
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        return execute(arguments, new ToolContext(null));
    }

    @Override
    public String execute(Map<String, Object> arguments, ToolContext ctx) {
        List<String> apps = ctx == null ? null : ctx.channelApps();
        if (apps == null) {
            return "No Discord apps/commands snapshot is available for this conversation "
                    + "(it did not originate from a Discord guild channel, or the list could not be retrieved).";
        }
        if (apps.isEmpty()) {
            return "No apps or slash commands are available in this Discord channel.";
        }

        StringBuilder sb = new StringBuilder("Discord apps and commands available in this channel:");
        for (String entry : apps) {
            sb.append('\n').append(entry);
        }
        return sb.toString();
    }
}
