package com.wallet.core.sync.entity;

import com.wallet.core.sync.SyncRunStatus;
import com.wallet.core.sync.SyncTrigger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncRun {

    private @Id UUID id;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncRunStatus status;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "accounts_count")
    private Integer accountsCount;

    @Column(name = "transactions_upserted")
    private Integer transactionsUpserted;

    @Column(name = "transactions_deleted")
    private Integer transactionsDeleted;
}
