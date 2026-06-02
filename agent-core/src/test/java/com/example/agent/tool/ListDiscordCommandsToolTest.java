package com.example.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ListDiscordCommandsToolTest {

    private final ListDiscordCommandsTool tool = new ListDiscordCommandsTool();

    @Test
    void reportsWhenNoAppsSnapshotAvailable() {
        // No context / no apps snapshot at all (e.g. non-Discord or DM origin).
        assertThat(tool.execute(Map.of(), new ToolContext(null)))
                .contains("No Discord apps/commands snapshot");
        assertThat(tool.execute(Map.of(), new ToolContext(null, "discord-channel-1", List.of())))
                .contains("No Discord apps/commands snapshot");
    }

    @Test
    void reportsWhenChannelHasNoAppsOrCommands() {
        var ctx = new ToolContext(null, "discord-channel-1", List.of(), List.of());
        assertThat(tool.execute(Map.of(), ctx))
                .contains("No apps or slash commands");
    }

    @Test
    void listsAppsAndCommands() {
        var ctx = new ToolContext(null, "discord-channel-1", List.of(),
                List.of("App: MidJourney", "Command: /imagine — make an image"));
        String out = tool.execute(Map.of(), ctx);
        assertThat(out)
                .contains("Discord apps and commands available")
                .contains("App: MidJourney")
                .contains("Command: /imagine — make an image");
    }
}
