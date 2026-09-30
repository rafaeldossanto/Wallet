package com.wallet.core.connection;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The connection module's API for the sync: read connections and record how a sync went.
 * The sync never touches the connection table directly.
 */
public interface ConnectionRegistry {

    Optional<LinkedConnection> find(UUID connectionId);

    /** Connections the scheduler should look at: everything not waiting on the user. */
    List<LinkedConnection> findSchedulable();

    void markSyncing(UUID connectionId);

    void recordSyncSucceeded(UUID connectionId, Instant providerUpdatedAt, Instant syncedAt,
                             String institutionName, String institutionImageUrl, Instant consentExpiresAt);

    /** The provider says the user has to act (log in again, renew consent). */
    void recordNeedsAttention(UUID connectionId);

    /** A failed or skipped sync puts a connection back to ACTIVE without touching its timestamps. */
    void recordSyncEnded(UUID connectionId);
}
