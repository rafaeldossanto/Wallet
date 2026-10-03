package com.wallet.core.sync.service;

import com.wallet.core.sync.SyncTrigger;
import com.wallet.core.sync.config.SyncProperties;
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

    /**
     * A plain {@code UPDATE}: when the connection was unlinked mid-sync its runs went with it (the
     * delete cascades), and there is nothing left to close.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(UUID runId, SyncOutcome outcome) {
        jdbcTemplate.update("""
                UPDATE sync_runs SET status = ?, error_code = ?, finished_at = ?, accounts_count = ?,
                                     transactions_upserted = ?, transactions_deleted = ?
                 WHERE id = ?
                """, outcome.status().name(), outcome.errorCode(), Timestamp.from(clock.instant()),
                outcome.accounts(), outcome.transactionsUpserted(), outcome.transactionsDeleted(), runId);
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
