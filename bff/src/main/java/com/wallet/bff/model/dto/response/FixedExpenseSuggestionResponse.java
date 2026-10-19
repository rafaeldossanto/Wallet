package com.wallet.bff.model.dto.response;

import java.time.LocalDate;

/**
 * A debit that repeats every month in a bank account, which the core proposes as a fixed expense.
 * Adding it is creating the fixed expense; the amount comes computed from the core.
 *
 * @param id     what dismissing it takes; opaque to the BFF
 * @param months in how many of the last months it showed up
 */
public record FixedExpenseSuggestionResponse(
        String id,
        String name,
        String amount,
        int dayOfMonth,
        String category,
        int months,
        LocalDate lastDate) {
}
