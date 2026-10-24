package com.wallet.core.planning.repository;

import com.wallet.core.planning.entity.FixedExpense;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FixedExpenseOrderTest {

    @Test
    void byDayFirstThenByNameWithinTheDay() {
        List<FixedExpense> sorted = FixedExpenseOrder.byDayThenName(List.of(
                expense("Escola", 10), expense("Zebra", 1), expense("água", 10), expense("Banana", 10), expense("Aluguel", 5)));

        assertThat(sorted).extracting(FixedExpense::getName).containsExactly("Zebra", "Aluguel", "água", "Banana", "Escola");
    }

    /** Accents and case count as little as to a Portuguese reader: Água comes before aluguel (g before l), éden after banana. */
    @Test
    void accentsAndCaseAreNotCodeUnits() {
        List<FixedExpense> sorted = FixedExpenseOrder.byDayThenName(List.of(
                expense("banana", 3), expense("Água", 3), expense("aluguel", 3), expense("Zumbi", 3), expense("éden", 3)));

        assertThat(sorted).extracting(FixedExpense::getName).containsExactly("Água", "aluguel", "banana", "éden", "Zumbi");
    }

    @Test
    void theSameNameOnTheSameDayKeepsAStableOrderById() {
        FixedExpense first = expense("Aluguel", 7);
        FixedExpense second = expense("Aluguel", 7);
        List<FixedExpense> expected = first.getId().compareTo(second.getId()) < 0 ? List.of(first, second) : List.of(second, first);

        assertThat(FixedExpenseOrder.byDayThenName(List.of(second, first))).isEqualTo(expected);
        assertThat(FixedExpenseOrder.byDayThenName(List.of(first, second))).isEqualTo(expected);
    }

    /** A name written with another key cannot be read: it goes last in its day, and the list still comes out. */
    @Test
    void anUnreadableNameGoesLastInItsDay() {
        List<FixedExpense> sorted = FixedExpenseOrder.byDayThenName(List.of(
                expense(null, 4), expense("Zebra", 4), expense("Aluguel", 4), expense("Primeiro", 2)));

        assertThat(sorted).extracting(FixedExpense::getName).containsExactly("Primeiro", "Aluguel", "Zebra", null);
    }

    private static FixedExpense expense(String name, int dayOfMonth) {
        return FixedExpense.builder().id(UUID.randomUUID()).dayOfMonth(dayOfMonth).name(name).build();
    }
}
