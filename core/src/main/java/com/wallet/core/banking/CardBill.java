package com.wallet.core.banking;

import com.wallet.core.shared.money.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A credit card bill with the purchases on it.
 *
 * @param total the bank's amount for a closed bill, so it always matches what will be charged;
 *              the purchases may not add up to it (fees, interest, a purchase the bank did not
 *              detail). For an open bill, the purchases so far
 * @param open  the bank has not closed it yet: estimated from the last bill's cycle, and it may
 *              still grow
 */
public record CardBill(
        UUID accountId,
        UUID connectionId,
        String cardName,
        String numberLastDigits,
        LocalDate dueDate,
        LocalDate closingDate,
        Money total,
        boolean open,
        List<CardBillItem> items) {
}
