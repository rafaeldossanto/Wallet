package com.wallet.core.connection;

import java.time.Instant;
import java.util.UUID;

/** What other modules (sync, insight) may know about a connection. */
public record LinkedConnection(
        UUID id,
        UUID userId,
        String providerItemId,
        ConnectionStatus status,
        Instant providerUpdatedAt,
        Instant lastSyncedAt) {
}
