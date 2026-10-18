package com.wallet.core.banking.service;

import com.wallet.core.banking.entity.AccountTransaction;
import com.wallet.core.banking.entity.CreditCardBill;
import com.wallet.core.banking.repository.BankingAggregates;
import com.wallet.core.shared.time.DateRange;
import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Which card purchases belong to which bill. The provider links a purchase to its bill when it
 * can (an installment booked months ago is linked to the bill that charges it now); a purchase
 * without a link goes to the bill whose cycle it was made in.
 */
@UtilityClass
class BillCycles {

    /** Without a closing date from the bank: Brazilian cards usually close about a week before the due date. */
    static final int DEFAULT_DAYS_FROM_CLOSING_TO_DUE = 7;

    static LocalDate closingOf(CreditCardBill bill) {
        return nonNull(bill.getClosingDate()) ? bill.getClosingDate() : bill.getDueDate().minusDays(DEFAULT_DAYS_FROM_CLOSING_TO_DUE);
    }

    /** From the day after {@code previous} closed to the day {@code bill} closed; a month when it is the first bill. */
    static DateRange cycleOf(CreditCardBill bill, CreditCardBill previous) {
        LocalDate closing = closingOf(bill);
        LocalDate start = isNull(previous) ? closing.minusMonths(1).plusDays(1) : closingOf(previous).plusDays(1);
        return new DateRange(start.isAfter(closing) ? closing : start, closing);
    }

    /** The bill after {@code last}, still open: its cycle closes a month after {@code last} closed. */
    static DateRange openCycleAfter(CreditCardBill last) {
        LocalDate lastClosing = closingOf(last);
        return new DateRange(lastClosing.plusDays(1), lastClosing.plusMonths(1));
    }

    /**
     * The purchases (and refunds) of one bill among {@code candidates}: the ones linked to it, and
     * the ones made in its cycle that are not linked to another known bill. Paying a previous bill
     * shows up on the card too, but it is not something the bill charges.
     *
     * @param providerBillId null for an open bill, which nothing is linked to yet
     */
    static List<AccountTransaction> itemsOf(String providerBillId, DateRange cycle, Set<String> knownBillIds,
                                            Collection<AccountTransaction> candidates) {
        Map<UUID, AccountTransaction> items = new LinkedHashMap<>();
        for (AccountTransaction transaction : candidates) {
            if (isNull(transaction.getDeletedAt()) && !isBillPayment(transaction)
                    && belongs(transaction, providerBillId, cycle, knownBillIds)) {
                items.putIfAbsent(transaction.getId(), transaction);
            }
        }
        return List.copyOf(items.values());
    }

    private static boolean belongs(AccountTransaction transaction, String providerBillId, DateRange cycle,
                                   Set<String> knownBillIds) {
        String link = transaction.getProviderBillId();
        if (nonNull(link) && knownBillIds.contains(link)) {
            return link.equals(providerBillId);
        }
        LocalDate day = transaction.getBookedOn();
        return !day.isBefore(cycle.from()) && !day.isAfter(cycle.to());
    }

    private static boolean isBillPayment(AccountTransaction transaction) {
        return nonNull(transaction.getCategory())
                && BankingAggregates.CARD_PAYMENT_CATEGORY.equals(transaction.getCategory().toLowerCase(Locale.ROOT));
    }
}
