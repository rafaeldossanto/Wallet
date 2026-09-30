package com.wallet.core.insight.dto;

import com.wallet.core.shared.money.Money;

import java.time.Instant;
import java.time.YearMonth;

/**
 * @param netWorth       cash + investments - credit card debt
 * @param monthIncome    money into bank accounts this month so far
 * @param monthExpenses  money out of bank accounts this month so far (card purchases count when the bill is paid)
 * @param lastSyncedAt   most recent sync among the user's connections; null before the first one
 */
public record OverviewResponse(
        Money netWorth,
        Money cashBalance,
        Money creditCardDebt,
        Money investmentsTotal,
        YearMonth month,
        Money monthIncome,
        Money monthExpenses,
        Instant lastSyncedAt) {
}
