package com.wallet.core.banking;

import com.wallet.core.shared.money.Money;

import java.time.LocalDate;

/**
 * Money that left a bank account (not a card) every month: the same description, about the same
 * day, a similar amount. Rent, the condo fee, the power bill paid by debit or boleto.
 *
 * @param description    the latest debit's description, as the bank wrote it
 * @param descriptionKey what the debits have in common: the description's words, folded, without
 *                       digits or punctuation ({@link com.wallet.core.shared.text.TextKeys#key})
 * @param category       the latest debit's provider category, or null
 * @param dayOfMonth     the day it usually leaves (the most common one)
 * @param amount         the average of the debits, to the cent
 * @param months         in how many months it showed up
 * @param lastDate       the latest debit
 */
public record RecurringDebit(
        String description,
        String descriptionKey,
        String category,
        int dayOfMonth,
        Money amount,
        int months,
        LocalDate lastDate) {
}
