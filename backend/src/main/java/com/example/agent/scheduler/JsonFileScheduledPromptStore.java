package com.example.agent.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default {@link ScheduledPromptStore}: keeps schedules in memory and mirrors them to a JSON file so
 * they survive a restart. Mirrors the spirit of {@code InMemoryConversationStore} — a dependency-free
 * default that lives in {@code agent-core}.
 *
 * <p>All mutating operations are synchronised; the on-disk mirror is written by a debounced
 * background flush (so a burst of mutations becomes one file rewrite, and callers never block on
 * disk I/O) and is forced on {@link #close()} so a graceful shutdown loses nothing. A secondary
 * index by delivery target keeps {@link #findByTarget} from scanning every schedule.
 */
public class JsonFileScheduledPromptStore implements ScheduledPromptStore, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JsonFileScheduledPromptStore.class);
    private static final long FLUSH_DELAY_MS = 1_000;

    private final ObjectMapper objectMapper;
    private final Path file;
    private final Map<String, ScheduledPrompt> byId = new LinkedHashMap<>();
    private final Map<String, Set<String>> idsByTarget = new HashMap<>();

    private final ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "scheduled-prompt-store-flush");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean flushPending = new AtomicBoolean();
    private final AtomicBoolean dirty = new AtomicBoolean();

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
                put(p);
            }
            log.info("Loaded {} scheduled prompt(s) from {}", byId.size(), file);
        } catch (IOException e) {
            log.warn("Could not read scheduled prompts from {}: {}", file, e.getMessage());
        }
    }

    /** Inserts into both indexes. Caller must hold the monitor. */
    private void put(ScheduledPrompt prompt) {
        ScheduledPrompt previous = byId.put(prompt.id(), prompt);
        if (previous != null) {
            unindex(previous);
        }
        if (prompt.deliveryTarget() != null) {
            idsByTarget.computeIfAbsent(prompt.deliveryTarget(), t -> new LinkedHashSet<>())
                    .add(prompt.id());
        }
    }

    /** Removes from the target index. Caller must hold the monitor. */
    private void unindex(ScheduledPrompt prompt) {
        if (prompt.deliveryTarget() == null) {
            return;
        }
        Set<String> ids = idsByTarget.get(prompt.deliveryTarget());
        if (ids != null) {
            ids.remove(prompt.id());
            if (ids.isEmpty()) {
                idsByTarget.remove(prompt.deliveryTarget());
            }
        }
    }

    /**
     * Coalesces mutations into a single file rewrite at most every {@link #FLUSH_DELAY_MS}. On a
     * hard crash at most that window of changes is lost; a graceful shutdown flushes via
     * {@link #close()}.
     */
    private void scheduleFlush() {
        dirty.set(true);
        if (flushPending.compareAndSet(false, true)) {
            try {
                flusher.schedule(this::runFlush, FLUSH_DELAY_MS, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.RejectedExecutionException e) {
                // Store is closing — close() flushes whatever is pending.
                flushPending.set(false);
            }
        }
    }

    private void runFlush() {
        flushPending.set(false);
        flushNow();
    }

    private synchronized void flushNow() {
        dirty.set(false);
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
        put(prompt);
        scheduleFlush();
    }

    @Override
    public synchronized boolean delete(String id) {
        ScheduledPrompt removed = byId.remove(id);
        if (removed == null) {
            return false;
        }
        unindex(removed);
        scheduleFlush();
        return true;
    }

    @Override
    public synchronized List<ScheduledPrompt> findAll() {
        return new ArrayList<>(byId.values());
    }

    @Override
    public synchronized List<ScheduledPrompt> findByTarget(String target) {
        Set<String> ids = target == null ? null : idsByTarget.get(target);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<ScheduledPrompt> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            ScheduledPrompt prompt = byId.get(id);
            if (prompt != null) {
                result.add(prompt);
            }
        }
        return result;
    }

    /** Stops the background flusher and writes any pending changes. Called by Spring on shutdown. */
    @Override
    public void close() {
        flusher.shutdownNow();
        flushPending.set(false);
        if (dirty.get()) {
            flushNow();
        }
    }
}
