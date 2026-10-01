package com.wallet.bff.model.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record BillResponse(
        UUID id,
        LocalDate dueDate,
        LocalDate closingDate,
        String totalAmount,
        String minimumPayment,
        String currencyCode) {
}
