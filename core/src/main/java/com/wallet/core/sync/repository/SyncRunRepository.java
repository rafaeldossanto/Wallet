package com.wallet.core.sync.repository;

import com.wallet.core.sync.SyncRunStatus;
import com.wallet.core.sync.entity.SyncRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {

    List<SyncRun> findByConnectionIdOrderByStartedAtDesc(UUID connectionId);

    /** With {@code RUNNING}: the latest sync that is over, whatever started it and however it ended. */
    Optional<SyncRun> findFirstByConnectionIdAndStatusNotOrderByStartedAtDesc(UUID connectionId, SyncRunStatus status);

    /**
     * Syncs that ended before {@code before}, except the latest finished one of each connection (the
     * one {@link #findFirstByConnectionIdAndStatusNotOrderByStartedAtDesc} finds, which the manual
     * sync's cooldown counts from). A running sync never matches.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM sync_runs r
             WHERE r.status <> 'RUNNING'
               AND COALESCE(r.finished_at, r.started_at) < :before
               AND EXISTS (SELECT 1 FROM sync_runs later
                            WHERE later.connection_id = r.connection_id
                              AND later.status <> 'RUNNING'
                              AND (later.started_at, later.id) > (r.started_at, r.id))
            """, nativeQuery = true)
    int deleteFinishedBeforeKeepingLatest(@Param("before") Instant before);
}
