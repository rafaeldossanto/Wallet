package com.wallet.core.banking.dto;

import com.wallet.core.shared.money.Money;

import java.time.LocalDate;
import java.util.UUID;

public record BillResponse(
        UUID id,
        LocalDate dueDate,
        LocalDate closingDate,
        Money totalAmount,
        Money minimumPayment,
        String currencyCode) {
}
