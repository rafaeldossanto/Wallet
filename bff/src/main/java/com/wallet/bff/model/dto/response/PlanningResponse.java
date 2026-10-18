package com.wallet.bff.model.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.isNull;

/**
 * The planning screen: the month's net salary minus each card's bill minus the fixed expenses.
 * Every amount, the totals included, comes computed from the core: the BFF does no arithmetic
 * with money. The BFF only adds each card's institution, which the core leaves to it.
 */
public record PlanningResponse(
        String month,
        Salary salary,
        List<Bill> bills,
        List<FixedExpense> fixedExpenses,
        Totals totals) {

    /** The same plan with each bill's institution; a card whose connection is unknown keeps none. */
    public PlanningResponse withInstitutions(Map<UUID, ConnectionResponse> connections) {
        List<Bill> named = bills.stream().map(bill -> bill.withInstitution(connections.get(bill.connectionId()))).toList();
        return new PlanningResponse(month, salary, named, fixedExpenses, totals);
    }

    /**
     * @param source RECEIVED, EXPECTED (the salary the user set), PREVIOUS_MONTH or NONE
     */
    public record Salary(String amount, LocalDate date, boolean predicted, String source, String expectedAmount) {
    }

    public record Bill(
            UUID accountId,
            UUID connectionId,
            String institutionName,
            String institutionImageUrl,
            String cardName,
            String numberLastDigits,
            LocalDate dueDate,
            LocalDate closingDate,
            String total,
            boolean open,
            List<BillItem> items,
            String otherCharges) {

        Bill withInstitution(ConnectionResponse connection) {
            return new Bill(accountId, connectionId,
                    isNull(connection) ? null : connection.institutionName(),
                    isNull(connection) ? null : connection.institutionImageUrl(),
                    cardName, numberLastDigits, dueDate, closingDate, total, open, items, otherCharges);
        }
    }

    /** @param direction OUTFLOW for a purchase, INFLOW for a refund */
    public record BillItem(
            UUID transactionId,
            LocalDate date,
            String description,
            String amount,
            String direction,
            String category,
            Integer installmentNumber,
            Integer installmentTotal) {
    }

    public record FixedExpense(UUID id, String name, String amount, int dayOfMonth, LocalDate date, String category) {
    }

    public record Totals(String salary, String bills, String fixedExpenses, String leftover) {
    }
}
