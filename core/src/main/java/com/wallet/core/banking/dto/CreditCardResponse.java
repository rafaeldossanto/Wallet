package com.wallet.core.banking.dto;

import com.wallet.core.shared.money.Money;

import java.util.UUID;

/**
 * @param currentBalance what is owed on the open bill right now
 * @param currentBill    the next bill to pay (earliest due today or later), or the latest one
 */
public record CreditCardResponse(
        UUID accountId,
        UUID connectionId,
        String name,
        String numberLastDigits,
        String currencyCode,
        Money creditLimit,
        Money availableCredit,
        Money currentBalance,
        BillResponse currentBill) {
}
