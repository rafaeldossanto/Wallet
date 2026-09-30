package com.wallet.core.banking;

import com.wallet.core.shared.time.DateRange;

import java.util.List;
import java.util.UUID;

/** The banking module's read API for the insight module. Every call is scoped to one user. */
public interface BankingQueries {

    List<AccountSummary> accounts(UUID userId);

    CashFlow cashFlow(UUID userId, DateRange period);

    /**
     * Outflows of every account, by category, largest first. Bank outflows categorized as
     * "Credit card payment" are left out: the purchases are already counted on the card.
     */
    List<CategorySpending> spendingByCategory(UUID userId, DateRange period);

    /** One point per day; a day without a snapshot repeats the last known balance. */
    List<BalancePoint> balanceHistory(UUID userId, DateRange period);
}
