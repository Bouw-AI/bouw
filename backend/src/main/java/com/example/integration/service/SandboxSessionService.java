package com.example.integration.service;

import com.example.agent.sandbox.RepositoryConfig;
import com.example.agent.sandbox.SandboxRuntime;
import com.example.agent.sandbox.SandboxSession;
import com.example.agent.sandbox.SandboxStatus;
import com.example.integration.sandbox.ProjectSandboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Lifecycle orchestration for isolated project-chat sandboxes.
 *
 * <p>Bridges the {@link DockerSandboxRuntime} (which talks to Docker) and the persistent
 * {@link SandboxSessionRepository} (which records each sandbox so it survives restarts). Project
 * chats call {@link #createForChat} to provision a container + clone, {@link #reconnect} when a chat
 * is reopened, and {@link #delete} when a chat is removed. The {@link SandboxCleanupService} calls
 * {@link #destroyExpired} on a schedule.
 */
@Service
public class SandboxSessionService {

    private static final Logger log = LoggerFactory.getLogger(SandboxSessionService.class);

    private final DockerSandboxRuntime runtime;
    private final SandboxSessionRepository repository;
    private final ProjectSandboxProperties properties;

    public SandboxSessionService(DockerSandboxRuntime runtime,
                                 SandboxSessionRepository repository,
                                 ProjectSandboxProperties properties) {
        this.runtime = runtime;
        this.repository = repository;
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /**
     * Provisions a brand-new isolated sandbox for a project chat: a Docker container + volume with the
     * repository cloned inside, persisted so it can be reconnected later. Fails loudly if the sandbox
     * runtime is disabled or Docker is unavailable — project chats never fall back to the host.
     */
    public SandboxSession createForChat(String chatSessionId, RepositoryConfig repository) {
        if (!properties.enabled()) {
            throw new IllegalStateException(
                    "Project sandboxes are disabled. Set hugin.sandbox.enabled=true to enable them.");
        }
        SandboxSession session = runtime.create(chatSessionId, repository);
        this.repository.save(session);
        log.info("Provisioned sandbox session {} for chat {}", session.sandboxId(), chatSessionId);
        return session;
    }

    public Optional<SandboxSession> find(String sandboxId) {
        return repository.findById(sandboxId);
    }

    public Optional<SandboxSession> findByChat(String chatSessionId) {
        return repository.findByChatSessionId(chatSessionId);
    }

    /**
     * Reconnects to an existing sandbox when its chat is reopened. Inspects the container and, when it
     * is stopped, restarts it; updates the persisted status accordingly. Returns the refreshed session,
     * or empty when no such sandbox is recorded.
     */
    public Optional<SandboxSession> reconnect(String sandboxId) {
        Optional<SandboxSession> found = repository.findById(sandboxId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        SandboxSession session = found.get();
        SandboxRuntime.SandboxState state;
        try {
            state = runtime.inspect(sandboxId);
        } catch (RuntimeException e) {
            log.warn("Could not inspect sandbox {}: {}", sandboxId, e.getMessage());
            return Optional.of(persistStatus(session, SandboxStatus.FAILED));
        }
        if (state == null || state.status() == SandboxStatus.DESTROYED) {
            return Optional.of(persistStatus(session, SandboxStatus.DESTROYED));
        }
        if (!state.running()) {
            try {
                runtime.restart(sandboxId);
            } catch (RuntimeException e) {
                log.warn("Could not restart sandbox {}: {}", sandboxId, e.getMessage());
                return Optional.of(persistStatus(session, SandboxStatus.FAILED));
            }
        }
        return Optional.of(touch(persistStatus(session, SandboxStatus.READY)));
    }

    /** Refreshes the idle clock for a sandbox that was just used by a request. */
    public SandboxSession touch(SandboxSession session) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(properties.idleTimeoutHours()));
        repository.touch(session.id().toString(), now, expiresAt);
        return session.touched(now, expiresAt);
    }

    public void touch(String sandboxId) {
        repository.findById(sandboxId).ifPresent(this::touch);
    }

    private SandboxSession persistStatus(SandboxSession session, SandboxStatus status) {
        if (session.status() != status) {
            repository.updateStatus(session.id().toString(), status);
        }
        return session.withStatus(status);
    }

    /** Destroys a sandbox's container + volume and removes its record. */
    public void delete(String sandboxId) {
        try {
            runtime.delete(sandboxId);
        } catch (RuntimeException e) {
            log.warn("Error destroying sandbox container {}: {}", sandboxId, e.getMessage());
        }
        repository.delete(sandboxId);
    }

    /** Destroys every sandbox whose idle expiry has passed. Invoked by the scheduled cleanup job. */
    public int destroyExpired() {
        List<SandboxSession> expired = repository.findExpired(Instant.now());
        for (SandboxSession session : expired) {
            log.info("Destroying expired sandbox {} (chat {}, idle since {})",
                    session.sandboxId(), session.chatSessionId(), session.lastUsedAt());
            delete(session.sandboxId());
        }
        return expired.size();
    }
}
