package com.wallet.bff.model.dto.response;

import java.time.Instant;

public record OverviewResponse(
        String netWorth,
        String cashBalance,
        String creditCardDebt,
        String investmentsTotal,
        String month,
        String monthIncome,
        String monthExpenses,
        Instant lastSyncedAt) {
}
