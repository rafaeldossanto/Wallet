package com.wallet.core.banking;

import com.wallet.core.shared.money.Money;

import java.time.LocalDate;

/** Money that landed in a bank account (not a card) under the provider's salary category. */
public record SalaryPayment(LocalDate date, Money amount) {
}
