package com.wallet.core.sync;

public enum SyncRunStatus {
    RUNNING,
    SUCCEEDED,
    /** Nothing to do (no news at the provider, provider still collecting, connection gone). */
    SKIPPED,
    FAILED
}
