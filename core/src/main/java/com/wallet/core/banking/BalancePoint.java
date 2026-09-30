package com.wallet.core.banking;

import com.wallet.core.shared.money.Money;

import java.time.LocalDate;

/** One day of balances: money in bank accounts and debt on credit cards. */
public record BalancePoint(LocalDate date, Money cash, Money creditCardDebt) {
}
