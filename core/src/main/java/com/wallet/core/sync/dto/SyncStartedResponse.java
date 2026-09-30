package com.wallet.core.sync.dto;

import com.wallet.core.sync.SyncRunStatus;

import java.util.UUID;

public record SyncStartedResponse(UUID syncRunId, SyncRunStatus status) {
}
