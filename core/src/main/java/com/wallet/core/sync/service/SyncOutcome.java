package com.wallet.core.sync.service;

import com.wallet.core.sync.SyncRunStatus;

/** How a run ended; becomes the final state of its {@code sync_runs} row. */
record SyncOutcome(SyncRunStatus status, String errorCode, int accounts, int transactionsUpserted,
                   int transactionsDeleted) {

    static SyncOutcome succeeded(int accounts, int upserted, int deleted) {
        return new SyncOutcome(SyncRunStatus.SUCCEEDED, null, accounts, upserted, deleted);
    }

    static SyncOutcome skipped(String reason) {
        return new SyncOutcome(SyncRunStatus.SKIPPED, reason, 0, 0, 0);
    }

    static SyncOutcome failed(String errorCode) {
        return new SyncOutcome(SyncRunStatus.FAILED, errorCode, 0, 0, 0);
    }
}
