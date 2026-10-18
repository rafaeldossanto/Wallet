package com.wallet.core.planning.dto;

import com.wallet.core.shared.money.Money;

import java.util.UUID;

public record FixedExpenseResponse(UUID id, String name, Money amount, int dayOfMonth, String category) {
}
