package com.wallet.core.sync.service;

import com.wallet.core.connection.ConnectionRegistry;
import com.wallet.core.connection.LinkedConnection;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import com.wallet.core.sync.SyncTrigger;
import com.wallet.core.sync.config.SyncProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

import static java.util.Objects.nonNull;

/** The "Atualizar" button: checked in the request, run in the background, answered with 202. */
@Service
public class ManualSyncService {

    private final ConnectionRegistry registry;
    private final SyncService syncService;
    private final SyncProperties properties;
    private final TaskExecutor executor;
    private final Clock clock;

    public ManualSyncService(ConnectionRegistry registry, SyncService syncService, SyncProperties properties,
                             @Qualifier("applicationTaskExecutor") TaskExecutor executor, Clock clock) {
        this.registry = registry;
        this.syncService = syncService;
        this.properties = properties;
        this.executor = executor;
        this.clock = clock;
    }

    public UUID request(UUID userId, UUID connectionId) {
        LinkedConnection connection = registry.find(connectionId)
                .filter(found -> found.userId().equals(userId))
                .orElseThrow(() -> new DomainException(ErrorType.NOT_FOUND, "connection.not_found", "No such connection"));
        if (nonNull(connection.lastSyncedAt())
                && connection.lastSyncedAt().plus(properties.manualCooldown()).isAfter(clock.instant())) {
            throw new DomainException(ErrorType.TOO_MANY_REQUESTS, "sync.too_soon",
                    "Synced less than " + properties.manualCooldown().toMinutes() + " minutes ago");
        }
        UUID runId = syncService.start(connectionId, SyncTrigger.MANUAL)
                .orElseThrow(() -> new DomainException(ErrorType.CONFLICT, "sync.in_progress", "A sync is already running"));
        executor.execute(() -> syncService.run(runId, connectionId, SyncTrigger.MANUAL));
        return runId;
    }
}
