package com.wallet.core.planning.mapper;

import com.wallet.core.banking.CardBill;
import com.wallet.core.banking.CardBillItem;
import com.wallet.core.banking.RecurringDebit;
import com.wallet.core.planning.dto.FixedExpenseRequest;
import com.wallet.core.planning.dto.FixedExpenseResponse;
import com.wallet.core.planning.dto.FixedExpenseSuggestionResponse;
import com.wallet.core.planning.dto.MonthPlanResponse;
import com.wallet.core.planning.entity.DismissedSuggestion;
import com.wallet.core.planning.entity.FixedExpense;
import com.wallet.core.planning.entity.PlanningSettings;
import com.wallet.core.shared.money.Money;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.springframework.util.StringUtils.hasText;

@UtilityClass
public class PlanningMapper {

    /** As long as a fixed expense's name may be. */
    private static final int NAME_MAX_LENGTH = 80;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    /** Kept in lower case in a name: "Plano de Saude", not "Plano De Saude". */
    private static final Set<String> CONNECTIVES = Set.of("a", "e", "o", "da", "das", "de", "do", "dos", "em", "na", "no");

    public FixedExpense toEntity(UUID userId, FixedExpenseRequest request, Instant now) {
        FixedExpense expense = FixedExpense.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .createdAt(now)
                .build();
        copy(request, expense, now);
        return expense;
    }

    /** The name is trimmed; a blank category is no category. */
    public void copy(FixedExpenseRequest request, FixedExpense target, Instant now) {
        target.setName(request.name().strip());
        target.setAmount(Money.of(request.amount()));
        target.setDayOfMonth(request.dayOfMonth());
        target.setCategory(hasText(request.category()) ? request.category().strip() : null);
        target.setUpdatedAt(now);
    }

    public FixedExpenseResponse toResponse(FixedExpense expense) {
        return new FixedExpenseResponse(expense.getId(), expense.getName(), expense.getAmount(), expense.getDayOfMonth(),
                expense.getCategory());
    }

    /** The expense's day in {@code month}: the 31st of a 30-day month is the 30th. */
    public MonthPlanResponse.FixedExpenseItem toMonthItem(FixedExpense expense, YearMonth month) {
        int day = Math.min(expense.getDayOfMonth(), month.lengthOfMonth());
        return new MonthPlanResponse.FixedExpenseItem(expense.getId(), expense.getName(), expense.getAmount(),
                expense.getDayOfMonth(), month.atDay(day), expense.getCategory());
    }

    public MonthPlanResponse.Bill toBill(CardBill bill, Money otherCharges) {
        return new MonthPlanResponse.Bill(bill.accountId(), bill.connectionId(), bill.cardName(), bill.numberLastDigits(),
                bill.dueDate(), bill.closingDate(), bill.total(), bill.open(),
                bill.items().stream().map(PlanningMapper::toBillItem).toList(), otherCharges);
    }

    public MonthPlanResponse.BillItem toBillItem(CardBillItem item) {
        return new MonthPlanResponse.BillItem(item.transactionId(), item.date(), item.description(), item.amount(),
                item.direction(), item.category(), item.installmentNumber(), item.installmentTotal());
    }

    public FixedExpenseSuggestionResponse toSuggestion(String id, RecurringDebit debit) {
        return new FixedExpenseSuggestionResponse(id, suggestedName(debit.description()), debit.amount(), debit.dayOfMonth(),
                debit.category(), debit.months(), debit.lastDate());
    }

    public DismissedSuggestion toDismissal(UUID userId, String descriptionKey, int dayOfMonth, Instant now) {
        return DismissedSuggestion.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .descriptionKey(descriptionKey)
                .dayOfMonth(dayOfMonth)
                .createdAt(now)
                .build();
    }

    /**
     * The bank's description as a name for the form: "PAG CONDOMINIO ED. SOL 10/26" is "Pag
     * Condominio Ed. Sol". Words without letters (dates, document numbers) go; a description in
     * capitals gets each word capitalized; what the bank already wrote in mixed case stays.
     */
    private static String suggestedName(String description) {
        List<String> words = Arrays.asList(description.strip().split("\\s+"));
        List<String> lettered = words.stream().filter(word -> word.chars().anyMatch(Character::isLetter)).toList();
        List<String> kept = lettered.isEmpty() ? words : lettered;
        String name = String.join(" ", kept);
        if (name.equals(name.toUpperCase(PT_BR))) {
            StringBuilder tidy = new StringBuilder();
            for (String word : kept) {
                String lower = word.toLowerCase(PT_BR);
                tidy.append(tidy.isEmpty() ? "" : " ").append(!tidy.isEmpty() && CONNECTIVES.contains(lower) ? lower : capitalize(lower));
            }
            name = tidy.toString();
        }
        return name.length() <= NAME_MAX_LENGTH ? name : name.substring(0, NAME_MAX_LENGTH).strip();
    }

    /** Every letter that does not follow another letter: "pag*condominio" is "Pag*Condominio", "d'agua" is "D'Agua". */
    private static String capitalize(String word) {
        StringBuilder out = new StringBuilder(word.length());
        boolean afterLetter = false;
        for (char character : word.toCharArray()) {
            out.append(afterLetter ? character : Character.toUpperCase(character));
            afterLetter = Character.isLetter(character);
        }
        return out.toString();
    }

    public PlanningSettings newSettings(UUID userId, Instant now) {
        return PlanningSettings.builder().userId(userId).createdAt(now).updatedAt(now).build();
    }
}
