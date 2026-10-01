package com.wallet.bff.model.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    @JsonIgnore
    public boolean isComplete() {
        return unavailable.isEmpty();
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
