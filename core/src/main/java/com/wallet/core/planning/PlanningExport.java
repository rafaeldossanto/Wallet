package com.wallet.core.planning;

import com.wallet.core.shared.money.Money;

import java.util.List;
import java.util.UUID;

/**
 * What the user typed into the plan, for the export of their data (LGPD, art. 18, II and V). The
 * field names are the export's schema: renaming one is a new {@code schemaVersion} of the export.
 *
 * @param expectedSalary {@code null} when the user never adjusted it
 */
public record PlanningExport(Money expectedSalary, List<FixedExpense> fixedExpenses) {

    /** @param dayOfMonth 1 to 31; in a shorter month it falls on the last day */
    public record FixedExpense(UUID id, String name, Money amount, int dayOfMonth, String category) {
    }
}
