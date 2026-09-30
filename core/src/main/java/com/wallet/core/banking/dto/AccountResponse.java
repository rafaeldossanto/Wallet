package com.wallet.core.banking.dto;

import com.wallet.core.shared.finance.AccountKind;
import com.wallet.core.shared.money.Money;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        UUID connectionId,
        AccountKind kind,
        String name,
        String numberLastDigits,
        String currencyCode,
        Money balance,
        Money creditLimit,
        Money availableCredit,
        Instant updatedAt) {
}
