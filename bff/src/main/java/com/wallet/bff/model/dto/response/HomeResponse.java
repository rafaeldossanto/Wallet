package com.wallet.bff.model.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.nonNull;

/**
 * The home screen in one call. A part that failed is empty and listed in {@code unavailable}
 * ("overview", "accounts", "connections", "creditCards", "recentTransactions"), so the app shows
 * a notice only on that block.
 */
public record HomeResponse(
        OverviewResponse overview,
        List<Institution> institutions,
        List<CreditCardResponse> creditCards,
        List<TransactionResponse> recentTransactions,
        List<String> unavailable) {

    private static final String SYNCING = "SYNCING";

    /**
     * Worth keeping only when every part loaded and no connection is still bringing data: a home
     * composed during a connection's first sync would show it empty for the cache's whole TTL.
     */
    @JsonIgnore
    public boolean isCacheable() {
        return unavailable.isEmpty() && institutions.stream()
                .allMatch(institution -> nonNull(institution.lastSyncedAt()) && !SYNCING.equals(institution.status()));
    }

    /** Bank accounts (not credit cards) grouped under the connection they came from. */
    public record Institution(
            UUID connectionId,
            String institutionName,
            String institutionImageUrl,
            String status,
            Instant lastSyncedAt,
            List<AccountResponse> accounts) {
    }
}
