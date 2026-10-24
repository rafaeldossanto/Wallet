package com.wallet.core.planning.repository;

import com.wallet.core.planning.entity.FixedExpense;
import lombok.experimental.UtilityClass;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * How the user's fixed expenses read, in one place: by day of the month and, within a day, by name.
 *
 * <p>The database used to do the second part ({@code ORDER BY day_of_month, name}). The name is
 * encrypted now, and ciphertext with a random IV sorts as noise, so the names are compared here after
 * they are decrypted. The comparison is the one Postgres used: a locale-aware {@link Collator} (letters
 * first, then accents, then case, lower case first), not the code-unit order of {@code String}, so
 * "água" still comes before "banana" and "Aluguel" next to "aluguel". {@code FixedExpenseOrderTest}
 * holds it to what the database says.
 */
@UtilityClass
public class FixedExpenseOrder {

    /** A name that cannot be read (written with another key) is {@code null} and goes last; the id settles a tie. */
    public static final Comparator<FixedExpense> BY_DAY_THEN_NAME = Comparator
            .comparingInt(FixedExpense::getDayOfMonth)
            .thenComparing(FixedExpense::getName, Comparator.nullsLast(nameOrder()))
            .thenComparing(FixedExpense::getId);

    public static List<FixedExpense> byDayThenName(List<FixedExpense> expenses) {
        return expenses.stream().sorted(BY_DAY_THEN_NAME).toList();
    }

    /** The order of two names alone, as the database sorted them. */
    public static Comparator<String> nameOrder() {
        Collator collator = Collator.getInstance(Locale.of("pt", "BR"));
        return collator::compare;
    }
}
