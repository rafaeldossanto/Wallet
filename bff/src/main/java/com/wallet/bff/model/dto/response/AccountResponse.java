package com.wallet.bff.model.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.UUID;

/** Money stays a string from the core to the app: the BFF never does arithmetic on it. */
public record AccountResponse(
        UUID id,
        UUID connectionId,
        String kind,
        String name,
        String numberLastDigits,
        String currencyCode,
        String balance,
        String creditLimit,
        String availableCredit,
        Instant updatedAt) {

    @JsonIgnore
    public boolean isCreditCard() {
        return "CREDIT_CARD".equals(kind);
    }
}
