package com.wallet.core.provider;

import java.time.Instant;

/**
 * One connection to one institution at the provider.
 *
 * @param lastUpdatedAt when the provider last collected data from the bank; the sync skips
 *                      a scheduled run when this has not moved
 * @param clientUserId  the Wallet user id the item was created for (null for Meu Pluggy items)
 */
public record ProviderItem(
        String id,
        String institutionName,
        String institutionImageUrl,
        ProviderItemStatus status,
        Instant lastUpdatedAt,
        Instant consentExpiresAt,
        String clientUserId) {
}
