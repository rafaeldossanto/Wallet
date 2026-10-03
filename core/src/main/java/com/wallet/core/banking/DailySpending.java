package com.wallet.core.banking;

import com.wallet.core.shared.money.Money;

import java.time.LocalDate;

/** What was spent on one day, by the same rule as spending by category. */
public record DailySpending(LocalDate date, Money total, int count) {
}
