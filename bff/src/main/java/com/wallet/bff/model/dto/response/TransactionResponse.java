package com.wallet.bff.model.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID accountId,
        LocalDate bookedOn,
        String description,
        String amount,
        String direction,
        String status,
        String category,
        Integer installmentNumber,
        Integer installmentTotal) {
}
