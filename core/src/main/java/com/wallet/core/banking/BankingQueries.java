package com.wallet.core.banking;

import com.wallet.core.shared.time.DateRange;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** The banking module's read API for the insight and planning modules. Every call is scoped to one user. */
public interface BankingQueries {

    List<AccountSummary> accounts(UUID userId);

    CashFlow cashFlow(UUID userId, DateRange period);

    /**
     * Outflows of every account, by category, largest first. Bank outflows categorized as
     * "Credit card payment" are left out: the purchases are already counted on the card.
     */
    List<CategorySpending> spendingByCategory(UUID userId, DateRange period);

    /** Same rule as {@link #spendingByCategory}, by day; only days with spending, oldest first. */
    List<DailySpending> spendingByDay(UUID userId, DateRange period);

    /** One point per day; a day without a snapshot repeats the last known balance. */
    List<BalancePoint> balanceHistory(UUID userId, DateRange period);

    /** Inflows into bank accounts categorized as salary by the provider, oldest first. */
    List<SalaryPayment> salaryPayments(UUID userId, DateRange period);

    /**
     * Every credit card's bill due in {@code month}, with its purchases, earliest due first. A
     * purchase belongs to a bill when the provider linked it to that bill or, without a link,
     * when it was made in the bill's cycle (the day after the previous closing to this closing).
     * A card whose bill for the month has not closed yet gets its open bill, estimated from the
     * last bill's cycle.
     */
    List<CardBill> billsDueIn(UUID userId, YearMonth month);

    /**
     * Debits of bank accounts (not cards) that repeat every month in {@code period}: the same
     * description (digits and punctuation aside), within a few days of the same day of the month,
     * no amount more than twice another, in at least {@code minMonths} months, and not stopped
     * (the next one is not overdue). Card bill payments are left out: the purchases are on the
     * card. A description that shows up more than twice a month on average is a habit, not a
     * bill. By day of the month.
     */
    List<RecurringDebit> recurringDebits(UUID userId, DateRange period, int minMonths);
}
