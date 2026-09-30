package com.wallet.core.sync.service;

import com.wallet.core.sync.SyncTrigger;
import com.wallet.core.sync.config.SyncProperties;
import com.wallet.core.sync.repository.SyncRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Opens and closes {@code sync_runs} rows, each in its own transaction so the record survives
 * whatever happens to the sync itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class SyncRunRecorder {

    private final JdbcTemplate jdbcTemplate;
    private final SyncRunRepository repository;
    private final SyncProperties properties;
    private final Clock clock;

    /**
     * Empty when another sync of the same connection is running. The partial unique index on
     * {@code status = 'RUNNING'} decides, so this holds across threads and instances. The insert
     * uses {@code ON CONFLICT DO NOTHING}: a failed insert would poison the transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UUID> tryStart(UUID connectionId, SyncTrigger trigger) {
        Instant now = clock.instant();
        releaseStaleRun(connectionId, now);
        UUID runId = UUID.randomUUID();
        try {
            int inserted = jdbcTemplate.update("""
                    INSERT INTO sync_runs (id, connection_id, trigger, status, started_at)
                    VALUES (?, ?, ?, 'RUNNING', ?)
                    ON CONFLICT (connection_id) WHERE status = 'RUNNING' DO NOTHING
                    """, runId, connectionId, trigger.name(), Timestamp.from(now));
            return inserted == 1 ? Optional.of(runId) : Optional.empty();
        } catch (DataIntegrityViolationException ex) {
            // The connection was unlinked in the meantime (foreign key): nothing to sync.
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(UUID runId, SyncOutcome outcome) {
        repository.findById(runId).ifPresent(run -> {
            run.setStatus(outcome.status());
            run.setErrorCode(outcome.errorCode());
            run.setFinishedAt(clock.instant());
            run.setAccountsCount(outcome.accounts());
            run.setTransactionsUpserted(outcome.transactionsUpserted());
            run.setTransactionsDeleted(outcome.transactionsDeleted());
        });
    }

    private void releaseStaleRun(UUID connectionId, Instant now) {
        int released = jdbcTemplate.update("""
                UPDATE sync_runs SET status = 'FAILED', error_code = 'sync.timed_out', finished_at = ?
                 WHERE connection_id = ? AND status = 'RUNNING' AND started_at < ?
                """, Timestamp.from(now), connectionId, Timestamp.from(now.minus(properties.staleRunAfter())));
        if (released > 0) {
            log.warn("[SYNC] Released a sync of connection {} stuck in RUNNING", connectionId);
        }
    }
}
