package com.wallet.core.sync.repository;

import com.wallet.core.sync.SyncRunStatus;
import com.wallet.core.sync.entity.SyncRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {

    List<SyncRun> findByConnectionIdOrderByStartedAtDesc(UUID connectionId);

    /** With {@code RUNNING}: the latest sync that is over, whatever started it and however it ended. */
    Optional<SyncRun> findFirstByConnectionIdAndStatusNotOrderByStartedAtDesc(UUID connectionId, SyncRunStatus status);
}
