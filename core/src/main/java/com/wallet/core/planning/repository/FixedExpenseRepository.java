package com.wallet.core.planning.repository;

import com.wallet.core.planning.entity.FixedExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public interface FixedExpenseRepository extends JpaRepository<FixedExpense, UUID> {

    /** How the user's list reads: by day, and within a day by name (accents and case count as little as a Portuguese reader's). */
    Comparator<FixedExpense> BY_DAY_THEN_NAME = Comparator
            .comparingInt(FixedExpense::getDayOfMonth)
            .thenComparing(FixedExpense::getName, Comparator.nullsLast(collator()))
            .thenComparing(FixedExpense::getId);

    /**
     * The database orders by day only, then by id so the order is stable. The name is encrypted, so
     * the tie-break inside a day cannot be done in SQL: see {@link #findOrderedByDayThenName}.
     */
    List<FixedExpense> findByUserIdOrderByDayOfMonthAscIdAsc(UUID userId);

    /**
     * Every fixed expense of the user by day and, within a day, by name, sorted here after the names are
     * decrypted. A user has at most 100 (planning.fixed_expense_limit).
     */
    default List<FixedExpense> findOrderedByDayThenName(UUID userId) {
        return findByUserIdOrderByDayOfMonthAscIdAsc(userId).stream().sorted(BY_DAY_THEN_NAME).toList();
    }

    /** A name that cannot be read (written with another key) is {@code null} and goes last. */
    private static Comparator<String> collator() {
        Collator collator = Collator.getInstance(Locale.of("pt", "BR"));
        return collator::compare;
    }

    Optional<FixedExpense> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
