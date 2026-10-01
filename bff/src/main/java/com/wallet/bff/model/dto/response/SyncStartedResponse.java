package com.wallet.bff.model.dto.response;

import java.util.UUID;

public record SyncStartedResponse(UUID syncRunId, String status) {
}
