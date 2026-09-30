package com.wallet.core.sync.repository;

import com.wallet.core.sync.entity.SyncRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {

    List<SyncRun> findByConnectionIdOrderByStartedAtDesc(UUID connectionId);
}
