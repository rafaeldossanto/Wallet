package com.wallet.bff.model.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ConnectionResponse(
        UUID id,
        String institutionName,
        String institutionImageUrl,
        String status,
        Instant lastSyncedAt,
        Instant consentExpiresAt,
        Instant createdAt) {
}
