package com.wallet.core.planning.repository;

import com.wallet.core.planning.entity.FixedExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FixedExpenseRepository extends JpaRepository<FixedExpense, UUID> {

    /**
     * The database orders by day only, then by id so the order is stable. The name is encrypted, so
     * the tie-break inside a day cannot be done in SQL: see {@link #findOrderedByDayThenName}.
     */
    List<FixedExpense> findByUserIdOrderByDayOfMonthAscIdAsc(UUID userId);

    /**
     * Every fixed expense of the user by day and, within a day, by name ({@link FixedExpenseOrder}),
     * sorted here after the names are decrypted. A user has at most 100 (planning.fixed_expense_limit).
     * The list, the month's plan, the suggestions and the export all read it through here.
     */
    default List<FixedExpense> findOrderedByDayThenName(UUID userId) {
        return FixedExpenseOrder.byDayThenName(findByUserIdOrderByDayOfMonthAscIdAsc(userId));
    }

    Optional<FixedExpense> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
