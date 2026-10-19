package com.wallet.core.planning.service;

import com.wallet.core.banking.RecurringDebit;
import com.wallet.core.planning.dto.FixedExpenseSuggestionResponse;
import com.wallet.core.planning.entity.DismissedSuggestion;
import com.wallet.core.planning.entity.FixedExpense;
import com.wallet.core.planning.mapper.PlanningMapper;
import com.wallet.core.shared.money.Money;
import com.wallet.core.shared.text.TextKeys;
import lombok.experimental.UtilityClass;

import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Which recurring debits become suggested fixed expenses: the ones the user has not registered
 * yet, under any name, and has not dismissed. Owns the rules; reads nothing.
 */
@UtilityClass
class FixedExpenseSuggestions {

    /** The same slack the debits get: a fixed expense on the 10th covers a debit that leaves on the 7th. */
    static final int DAY_TOLERANCE = 3;

    /** A fixed expense this close to a debit's amount, on about its day, is that debit under another name. */
    static final int SAME_AMOUNT_PERCENT = 15;

    /**
     * @param fingerprint turns a debit's description key into the key a dismissal is kept under
     * @return by day of the month, then name
     */
    List<FixedExpenseSuggestionResponse> select(List<RecurringDebit> debits, List<FixedExpense> fixedExpenses,
                                                List<DismissedSuggestion> dismissed, UnaryOperator<String> fingerprint) {
        return debits.stream()
                .filter(debit -> fixedExpenses.stream().noneMatch(expense -> covers(expense, debit)))
                .map(debit -> new Candidate(new SuggestionId(fingerprint.apply(debit.descriptionKey()), debit.dayOfMonth()), debit))
                .filter(candidate -> !isDismissed(candidate.id(), dismissed))
                .map(candidate -> PlanningMapper.toSuggestion(candidate.id().value(), candidate.debit()))
                .sorted(Comparator.comparingInt(FixedExpenseSuggestionResponse::dayOfMonth)
                        .thenComparing(FixedExpenseSuggestionResponse::name))
                .toList();
    }

    /**
     * About the same day, and the same name (one has the other's words: "Aluguel" and "ALUGUEL
     * APARTAMENTO") or the same amount give or take {@value #SAME_AMOUNT_PERCENT}% ("Luz" for
     * "ENEL DISTRIBUICAO").
     */
    boolean covers(FixedExpense expense, RecurringDebit debit) {
        if (!nearDay(expense.getDayOfMonth(), debit.dayOfMonth())) {
            return false;
        }
        String name = TextKeys.key(expense.getName());
        return TextKeys.containsWords(debit.descriptionKey(), name)
                || TextKeys.containsWords(name, debit.descriptionKey())
                || sameAmount(expense.getAmount(), debit.amount());
    }

    /** A dismissal covers the debit's usual day give or take the tolerance, since that day can move. */
    boolean isDismissed(SuggestionId id, List<DismissedSuggestion> dismissed) {
        return dismissed.stream().anyMatch(dismissal -> dismissal.getDescriptionKey().equals(id.descriptionKey())
                && nearDay(dismissal.getDayOfMonth(), id.dayOfMonth()));
    }

    /** Across the turn of the month too: the 30th and the 2nd are three days apart. */
    boolean nearDay(int one, int other) {
        int distance = Math.abs(one - other);
        return Math.min(distance, 31 - distance) <= DAY_TOLERANCE;
    }

    private static boolean sameAmount(Money amount, Money reference) {
        return amount.minus(reference).abs().times(100).compareTo(reference.times(SAME_AMOUNT_PERCENT)) <= 0;
    }

    private record Candidate(SuggestionId id, RecurringDebit debit) {
    }
}
