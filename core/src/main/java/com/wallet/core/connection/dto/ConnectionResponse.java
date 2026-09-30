package com.wallet.core.connection.dto;

import com.wallet.core.connection.ConnectionStatus;

import java.time.Instant;
import java.util.UUID;

public record ConnectionResponse(
        UUID id,
        String institutionName,
        String institutionImageUrl,
        ConnectionStatus status,
        Instant lastSyncedAt,
        Instant consentExpiresAt,
        Instant createdAt) {
}
