package com.wallet.core.provider;

import com.wallet.core.shared.money.Money;

import java.time.LocalDate;

public record ProviderBill(
        String id,
        String accountId,
        LocalDate dueDate,
        LocalDate closingDate,
        Money totalAmount,
        Money minimumPayment,
        String currencyCode) {
}
