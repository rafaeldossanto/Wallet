package com.wallet.core.planning.dto;

import com.wallet.core.shared.money.Money;

import java.time.LocalDate;

/**
 * A debit that repeats every month in a bank account, proposed as a fixed expense. Adding it is
 * creating a fixed expense with these values (the user may change them first).
 *
 * @param id         what dismissing it takes; the same while the debit keeps its description and day
 * @param name       the bank's description, tidied up for the form (dates and numbers dropped)
 * @param amount     the average of the debits, to the cent
 * @param dayOfMonth the day it usually leaves
 * @param category   the latest debit's provider category, or null
 * @param months     in how many of the last months it showed up
 * @param lastDate   the latest debit
 */
public record FixedExpenseSuggestionResponse(
        String id,
        String name,
        Money amount,
        int dayOfMonth,
        String category,
        int months,
        LocalDate lastDate) {
}
