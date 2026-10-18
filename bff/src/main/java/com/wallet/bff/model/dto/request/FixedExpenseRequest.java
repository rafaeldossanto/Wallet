package com.wallet.bff.model.dto.request;

/** Relayed as it came: the core validates it and answers in its own codes. Money as text. */
public record FixedExpenseRequest(String name, String amount, Integer dayOfMonth, String category) {
}
