package com.example.agent.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default {@link ScheduledPromptStore}: keeps schedules in memory and mirrors them to a JSON file so
 * they survive a restart. Mirrors the spirit of {@code InMemoryConversationStore} — a dependency-free
 * default that lives in {@code agent-core}.
 *
 * <p>All mutating operations are synchronised and rewrite the whole file (the set of schedules is
 * small), keeping the on-disk copy consistent without a database.
 */
public class JsonFileScheduledPromptStore implements ScheduledPromptStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileScheduledPromptStore.class);

    private final ObjectMapper objectMapper;
    private final Path file;
    private final Map<String, ScheduledPrompt> byId = new LinkedHashMap<>();

    public JsonFileScheduledPromptStore(ObjectMapper objectMapper, String storeFile) {
        this.objectMapper = objectMapper;
        this.file = resolve(storeFile);
        load();
    }

    private static Path resolve(String storeFile) {
        String expanded = storeFile.startsWith("~/")
                ? System.getProperty("user.home") + storeFile.substring(1)
                : storeFile;
        return Path.of(expanded).toAbsolutePath().normalize();
    }

    private synchronized void load() {
        if (!Files.isReadable(file)) {
            return;
        }
        try {
            List<ScheduledPrompt> loaded = objectMapper.readValue(
                    Files.readAllBytes(file), new TypeReference<List<ScheduledPrompt>>() {});
            for (ScheduledPrompt p : loaded) {
                byId.put(p.id(), p);
            }
            log.info("Loaded {} scheduled prompt(s) from {}", byId.size(), file);
        } catch (IOException e) {
            log.warn("Could not read scheduled prompts from {}: {}", file, e.getMessage());
        }
    }

    private void flush() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(file.toFile(), new ArrayList<>(byId.values()));
        } catch (IOException e) {
            log.warn("Could not persist scheduled prompts to {}: {}", file, e.getMessage());
        }
    }

    @Override
    public synchronized void save(ScheduledPrompt prompt) {
        byId.put(prompt.id(), prompt);
        flush();
    }

    @Override
    public synchronized void delete(String id) {
        if (byId.remove(id) != null) {
            flush();
        }
    }

    @Override
    public synchronized List<ScheduledPrompt> findAll() {
        return new ArrayList<>(byId.values());
    }
}
