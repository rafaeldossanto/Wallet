package com.wallet.core.planning.dto;

import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.money.Money;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * One month's plan: the net salary, minus each card's bill due that month, minus the fixed
 * expenses, is what is left.
 */
public record MonthPlanResponse(
        YearMonth month,
        Salary salary,
        List<Bill> bills,
        List<FixedExpenseItem> fixedExpenses,
        Totals totals) {

    public enum SalarySource {
        /** Landed in the month: what the statement shows. */
        RECEIVED,
        /** Not landed yet; the salary the user set. */
        EXPECTED,
        /** Not landed yet; the last salary received, from up to three months back. */
        PREVIOUS_MONTH,
        /** Not landed and nothing to go by. */
        NONE
    }

    /**
     * @param amount         null when {@code source} is {@code NONE}
     * @param date           when it landed (the last deposit when there were several); for a
     *                       prediction, the day it should land, when the history says
     * @param predicted      the amount has not landed yet
     * @param expectedAmount the salary the user set, also when the real one already landed
     */
    public record Salary(Money amount, LocalDate date, boolean predicted, SalarySource source, Money expectedAmount) {
    }

    /**
     * @param total        the bank's bill amount for a closed bill, so it always matches
     * @param open         not closed yet: the purchases so far, the dates estimated from the last bill
     * @param items        purchases largest first, then refunds
     * @param otherCharges what the bill charges beyond the listed purchases (fees, interest, a
     *                     purchase the bank did not detail); negative when credits lower it
     */
    public record Bill(
            UUID accountId,
            UUID connectionId,
            String cardName,
            String numberLastDigits,
            LocalDate dueDate,
            LocalDate closingDate,
            Money total,
            boolean open,
            List<BillItem> items,
            Money otherCharges) {
    }

    public record BillItem(
            UUID transactionId,
            LocalDate date,
            String description,
            Money amount,
            Direction direction,
            String category,
            Integer installmentNumber,
            Integer installmentTotal) {
    }

    /** @param date the day it falls on in this month */
    public record FixedExpenseItem(UUID id, String name, Money amount, int dayOfMonth, LocalDate date, String category) {
    }

    /** @param leftover salary - bills - fixed expenses; negative when the month does not close */
    public record Totals(Money salary, Money bills, Money fixedExpenses, Money leftover) {
    }
}
