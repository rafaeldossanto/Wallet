package com.wallet.bff.model.dto.response;

import java.util.UUID;

public record CreditCardResponse(
        UUID accountId,
        UUID connectionId,
        String name,
        String numberLastDigits,
        String currencyCode,
        String creditLimit,
        String availableCredit,
        String currentBalance,
        BillResponse currentBill) {
}
