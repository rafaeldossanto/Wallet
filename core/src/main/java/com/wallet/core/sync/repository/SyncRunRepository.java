package com.wallet.core.sync.repository;

import com.wallet.core.sync.entity.SyncRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {

    List<SyncRun> findByConnectionIdOrderByStartedAtDesc(UUID connectionId);

    /**
     * The latest sync that read (or tried to read) the bank and is over, whatever started it and however
     * it ended: what the manual cooldown counts from. A run skipped only because the authorization had
     * ended read nothing, so it does not count: renewing the authorization must not wait on it.
     * Ask for one with {@code PageRequest.of(0, 1)}.
     */
    @Query("""
            select r from SyncRun r
             where r.connectionId = :connectionId and r.status <> com.wallet.core.sync.SyncRunStatus.RUNNING
               and (r.errorCode is null or r.errorCode <> 'connection.consent_expired')
             order by r.startedAt desc
            """)
    List<SyncRun> findLatestOver(@Param("connectionId") UUID connectionId, Pageable page);

    /**
     * Syncs that ended before {@code before}, except the latest finished one of each connection (the
     * one the manual sync's cooldown counts from, nearly: {@link #findLatestOver} skips the runs of an
     * ended authorization, which only matters after the retention period, when the cooldown is long
     * gone). A running sync never matches.
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
