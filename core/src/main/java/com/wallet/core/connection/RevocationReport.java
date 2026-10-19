package com.wallet.core.connection;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * What {@link ConnectionRevocation#revokeAll} did.
 *
 * @param connectionIds the connections it went through
 * @param revoked       revoked at the provider (or already gone there)
 * @param released      only let go of: not the Wallet's to revoke (Meu Pluggy items)
 * @param failed        the provider failed; the item may still exist there
 */
public record RevocationReport(Set<UUID> connectionIds, int revoked, int released, int failed) {

    public RevocationReport {
        connectionIds = Set.copyOf(connectionIds);
    }

    public RevocationReport plus(RevocationReport other) {
        Set<UUID> all = new LinkedHashSet<>(connectionIds);
        all.addAll(other.connectionIds());
        return new RevocationReport(all, revoked + other.revoked(), released + other.released(), failed + other.failed());
    }
}
