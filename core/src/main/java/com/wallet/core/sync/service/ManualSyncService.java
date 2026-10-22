package com.wallet.core.sync.service;

import com.wallet.core.connection.ConnectionRegistry;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import com.wallet.core.sync.SyncRunStatus;
import com.wallet.core.sync.SyncTrigger;
import com.wallet.core.sync.config.SyncProperties;
import com.wallet.core.sync.entity.SyncRun;
import com.wallet.core.sync.repository.SyncRunRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/** The "Atualizar" button: checked in the request, run in the background, answered with 202. */
@Service
public class ManualSyncService {

    private final ConnectionRegistry registry;
    private final SyncService syncService;
    private final SyncRunRepository runs;
    private final SyncProperties properties;
    private final TaskExecutor executor;
    private final Clock clock;

    public ManualSyncService(ConnectionRegistry registry, SyncService syncService, SyncRunRepository runs,
                             SyncProperties properties, @Qualifier("applicationTaskExecutor") TaskExecutor executor,
                             Clock clock) {
        this.registry = registry;
        this.syncService = syncService;
        this.runs = runs;
        this.properties = properties;
        this.executor = executor;
        this.clock = clock;
    }

    /**
     * The cooldown counts from the end of the connection's latest sync, whatever started it (the
     * first sync after linking included) and however it ended. Counting only successes let a
     * connection that never synced, or always fails, send a full read to the provider on every tap.
     * A sync still running answers 409 instead.
     */
    public UUID request(UUID userId, UUID connectionId) {
        registry.find(connectionId)
                .filter(found -> found.userId().equals(userId))
                .orElseThrow(() -> new DomainException(ErrorType.NOT_FOUND, "connection.not_found", "No such connection"));
        boolean tooSoon = runs.findFirstByConnectionIdAndStatusNotOrderByStartedAtDesc(connectionId, SyncRunStatus.RUNNING)
                .map(SyncRun::getFinishedAt)
                .filter(finishedAt -> finishedAt.plus(properties.manualCooldown()).isAfter(clock.instant()))
                .isPresent();
        if (tooSoon) {
            throw new DomainException(ErrorType.TOO_MANY_REQUESTS, "sync.too_soon",
                    "Last sync attempt ended less than " + properties.manualCooldown().toMinutes() + " minutes ago");
        }
        UUID runId = syncService.start(connectionId, SyncTrigger.MANUAL)
                .orElseThrow(() -> new DomainException(ErrorType.CONFLICT, "sync.in_progress", "A sync is already running"));
        executor.execute(() -> syncService.run(runId, connectionId, SyncTrigger.MANUAL));
        return runId;
    }
}
