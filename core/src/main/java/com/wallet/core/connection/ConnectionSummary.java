package com.wallet.core.connection;

import java.time.Instant;
import java.util.UUID;

/**
 * A connection as the user sees it, for the export of their data: the bank, its state and how long
 * the consent lasts. Nothing that identifies the item at the provider.
 *
 * @param linkedAt         when the user linked it
 * @param consentExpiresAt {@code null} when the provider did not say
 */
public record ConnectionSummary(
        UUID id,
        String institutionName,
        ConnectionStatus status,
        Instant linkedAt,
        Instant lastSyncedAt,
        Instant consentExpiresAt) {
}
