package com.wallet.core.planning.service;

import com.wallet.core.banking.RecurringDebit;
import com.wallet.core.planning.dto.FixedExpenseSuggestionResponse;
import com.wallet.core.planning.entity.DismissedSuggestion;
import com.wallet.core.planning.entity.FixedExpense;
import com.wallet.core.shared.money.Money;
import com.wallet.core.shared.text.TextKeys;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/** Which recurring debits are suggested, on made-up numbers. */
class FixedExpenseSuggestionsTest {

    /** A stand-in for the keyed fingerprint: 32 hex characters that differ per key. */
    private static final UnaryOperator<String> FINGERPRINT = key -> "%032x".formatted(key.hashCode() & 0xffffffffL);

    private static final RecurringDebit CONDO = debit("PAG CONDOMINIO ED. SOL 10/26", 9, "690.00", "Housing");
    private static final RecurringDebit POWER = debit("ENEL DISTRIBUICAO SP", 20, "195.17", "Electricity");

    @Test
    void aRecurringDebitBecomesASuggestionWithATidyName() {
        List<FixedExpenseSuggestionResponse> suggestions = select(List.of(CONDO), List.of(), List.of());

        assertThat(suggestions).containsExactly(new FixedExpenseSuggestionResponse(
                FINGERPRINT.apply("pag condominio ed sol") + "-9", "Pag Condominio Ed. Sol", Money.of("690.00"), 9, "Housing",
                3, LocalDate.parse("2026-10-09")));
    }

    @Test
    void namesAreTidiedOnlyWhenTheBankShouted() {
        assertThat(nameOf("PLANO DE SAUDE")).isEqualTo("Plano de Saude");
        assertThat(nameOf("PAG*CONDOMÍNIO D'AGUA 09/2026")).isEqualTo("Pag*Condomínio D'Agua");
        assertThat(nameOf("Netflix.com")).isEqualTo("Netflix.com");
        assertThat(nameOf("Escola de Inglês 10/26")).isEqualTo("Escola de Inglês");
        assertThat(nameOf("ENERGIA " + "MUITO ".repeat(20))).hasSizeLessThanOrEqualTo(80).startsWith("Energia Muito").doesNotEndWith(" ");
    }

    @Test
    void aFixedExpenseWithTheSameWordsOnAboutTheSameDayCoversADebit() {
        assertThat(select(List.of(CONDO), List.of(fixed("Condomínio", "650.00", 10)), List.of())).isEmpty();
        assertThat(select(List.of(debit("ALUGUEL", 7, "2800.00", "Rent")), List.of(fixed("Aluguel apartamento", "2800.00", 5)),
                List.of())).isEmpty();
    }

    /** "Luz" is not in "ENEL DISTRIBUICAO SP", but R$ 200 on the 20th is that bill. */
    @Test
    void aFixedExpenseWithAboutTheSameAmountOnAboutTheSameDayCoversADebit() {
        assertThat(select(List.of(POWER), List.of(fixed("Luz", "200.00", 20)), List.of())).isEmpty();
        assertThat(select(List.of(POWER), List.of(fixed("Luz", "230.00", 20)), List.of())).hasSize(1);
    }

    @Test
    void onAnotherDayItIsAnotherExpense() {
        assertThat(select(List.of(CONDO), List.of(fixed("Condomínio", "690.00", 20)), List.of())).hasSize(1);
    }

    @Test
    void daysAreCloseAcrossTheTurnOfTheMonth() {
        assertThat(FixedExpenseSuggestions.nearDay(30, 2)).isTrue();
        assertThat(FixedExpenseSuggestions.nearDay(31, 1)).isTrue();
        assertThat(FixedExpenseSuggestions.nearDay(9, 12)).isTrue();
        assertThat(FixedExpenseSuggestions.nearDay(9, 13)).isFalse();
        assertThat(FixedExpenseSuggestions.nearDay(1, 28)).isFalse();
    }

    @Test
    void aDismissedSuggestionStaysDismissedEvenIfItsDayMoves() {
        String condoKey = FINGERPRINT.apply("pag condominio ed sol");
        List<DismissedSuggestion> dismissed = List.of(dismissal(condoKey, 9));

        assertThat(select(List.of(CONDO, POWER), List.of(), dismissed)).extracting(FixedExpenseSuggestionResponse::name)
                .containsExactly("Enel Distribuicao Sp");
        assertThat(select(List.of(debit("PAG CONDOMINIO ED. SOL 11/26", 11, "690.00", "Housing")), List.of(), dismissed))
                .isEmpty();
        assertThat(select(List.of(debit("PAG CONDOMINIO ED. SOL 11/26", 25, "120.00", "Housing")), List.of(), dismissed))
                .hasSize(1);
    }

    @Test
    void suggestionsComeByDayThenName() {
        List<FixedExpenseSuggestionResponse> suggestions = select(
                List.of(POWER, debit("VIVO FIBRA", 9, "129.90", null), CONDO), List.of(), List.of());

        assertThat(suggestions).extracting(FixedExpenseSuggestionResponse::name)
                .containsExactly("Pag Condominio Ed. Sol", "Vivo Fibra", "Enel Distribuicao Sp");
    }

    @Test
    void onlyTheIdsThisCoreHandsOutParse() {
        String key = "9f86d081884c7d659a2feaa0c55ad015";

        assertThat(SuggestionId.parse(key + "-9")).contains(new SuggestionId(key, 9));
        assertThat(SuggestionId.parse(key + "-31").map(SuggestionId::value)).contains(key + "-31");
        for (String bad : List.of(key + "-0", key + "-32", key + "-09", key.toUpperCase() + "-9", key, "../fixed-expenses", "")) {
            assertThat(SuggestionId.parse(bad)).as(bad).isEmpty();
        }
        assertThat(SuggestionId.parse(null)).isEmpty();
    }

    private static List<FixedExpenseSuggestionResponse> select(List<RecurringDebit> debits, List<FixedExpense> fixed,
                                                               List<DismissedSuggestion> dismissed) {
        return FixedExpenseSuggestions.select(debits, fixed, dismissed, FINGERPRINT);
    }

    private static String nameOf(String description) {
        return select(List.of(debit(description, 5, "10.00", null)), List.of(), List.of()).getFirst().name();
    }

    private static RecurringDebit debit(String description, int day, String amount, String category) {
        return new RecurringDebit(description, TextKeys.key(description), category, day, Money.of(amount), 3,
                LocalDate.parse("2026-10-09"));
    }

    private static FixedExpense fixed(String name, String amount, int day) {
        Instant now = Instant.parse("2026-10-18T12:00:00Z");
        return FixedExpense.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).name(name).amount(Money.of(amount))
                .dayOfMonth(day).createdAt(now).updatedAt(now).build();
    }

    private static DismissedSuggestion dismissal(String key, int day) {
        return DismissedSuggestion.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).descriptionKey(key)
                .dayOfMonth(day).createdAt(Instant.parse("2026-10-18T12:00:00Z")).build();
    }
}
