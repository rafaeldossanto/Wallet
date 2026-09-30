package com.wallet.core.provider;

import com.wallet.core.shared.finance.AccountKind;
import com.wallet.core.shared.money.Money;

/**
 * @param balance         money in the account; for a credit card, the amount owed on the open bill
 * @param creditLimit     credit cards only, otherwise null
 * @param availableCredit credit cards only, otherwise null
 */
public record ProviderAccount(
        String id,
        String itemId,
        AccountKind kind,
        String name,
        String numberLastDigits,
        String currencyCode,
        Money balance,
        Money creditLimit,
        Money availableCredit) {
}
