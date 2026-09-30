package com.wallet.core.banking;

import com.wallet.core.shared.finance.AccountKind;
import com.wallet.core.shared.money.Money;

import java.util.UUID;

/** @param balance for a credit card, the amount owed on the open bill */
public record AccountSummary(
        UUID id,
        UUID connectionId,
        AccountKind kind,
        String name,
        String numberLastDigits,
        String currencyCode,
        Money balance,
        Money creditLimit,
        Money availableCredit) {
}
