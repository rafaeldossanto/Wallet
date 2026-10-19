package com.wallet.bff.model.dto.response;

import java.util.UUID;

public record FixedExpenseResponse(UUID id, String name, String amount, int dayOfMonth, String category) {
}
