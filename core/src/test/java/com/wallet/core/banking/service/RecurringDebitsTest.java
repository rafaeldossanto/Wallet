package com.wallet.core.banking.service;

import com.wallet.core.banking.RecurringDebit;
import com.wallet.core.banking.entity.AccountTransaction;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.finance.TransactionStatus;
import com.wallet.core.shared.money.Money;
import com.wallet.core.shared.time.DateRange;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Which debits repeat every month, on made-up statements. Today is 2026-10-18; July to October is looked at. */
class RecurringDebitsTest {

    private static final DateRange PERIOD = new DateRange(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-10-18"));
    private static final int MIN_MONTHS = 3;

    @Test
    void aDebitOnTheSameDayEveryMonthIsFound() {
        List<RecurringDebit> found = find(
                debit("2026-07-09", "CONDOMINIO EDIFICIO SOL", "690.00"),
                debit("2026-08-09", "CONDOMINIO EDIFICIO SOL", "690.00"),
                debit("2026-09-09", "CONDOMINIO EDIFICIO SOL", "690.00"),
                debit("2026-10-09", "CONDOMINIO EDIFICIO SOL", "690.00"));

        assertThat(found).containsExactly(new RecurringDebit("CONDOMINIO EDIFICIO SOL", "condominio edificio sol", "Housing",
                9, Money.of("690.00"), 4, LocalDate.parse("2026-10-09")));
    }

    /** The bank writes the month and a document number into the description, and a weekend moves the day. */
    @Test
    void datesNumbersAndAWeekendDoNotBreakIt() {
        List<RecurringDebit> found = find(
                debit("2026-07-09", "PAG*CONDOMINIO 07/26 DOC 0012", "690.00"),
                debit("2026-08-11", "PAG CONDOMINIO 08/26 DOC 0345", "690.00"),
                debit("2026-09-09", "PAG*CONDOMINIO 09/26 DOC 0678", "690.00"),
                debit("2026-10-09", "PAG*CONDOMINIO 10/26 DOC 0901", "690.00"));

        assertThat(found).singleElement().satisfies(debit -> {
            assertThat(debit.descriptionKey()).isEqualTo("pag condominio doc");
            assertThat(debit.description()).isEqualTo("PAG*CONDOMINIO 10/26 DOC 0901");
            assertThat(debit.dayOfMonth()).isEqualTo(9);
            assertThat(debit.months()).isEqualTo(4);
        });
    }

    /** Power swings with the season: the amount is the average, to the cent. */
    @Test
    void aBillThatSwingsIsTheAverageOfItsMonths() {
        List<RecurringDebit> found = find(
                debit("2026-07-20", "CONTA DE LUZ", "180.00"),
                debit("2026-08-20", "CONTA DE LUZ", "210.00"),
                debit("2026-09-21", "CONTA DE LUZ", "195.50"));

        assertThat(found).singleElement().satisfies(debit -> {
            assertThat(debit.amount()).isEqualTo(Money.of("195.17"));
            assertThat(debit.dayOfMonth()).isEqualTo(20);
            assertThat(debit.months()).isEqualTo(3);
            assertThat(debit.lastDate()).isEqualTo(LocalDate.parse("2026-09-21"));
        });
    }

    @Test
    void twoMonthsAreNotEnough() {
        assertThat(find(debit("2026-09-05", "PIX ENVIADO ACADEMIA", "99.90"),
                debit("2026-10-05", "PIX ENVIADO ACADEMIA", "99.90"))).isEmpty();
    }

    /** October's should have left on the 3rd: it was cancelled, or moved to the card. */
    @Test
    void aDebitThatStoppedIsNotFound() {
        assertThat(find(debit("2026-07-03", "ASSINATURA REVISTA", "29.90"),
                debit("2026-08-03", "ASSINATURA REVISTA", "29.90"),
                debit("2026-09-03", "ASSINATURA REVISTA", "29.90"))).isEmpty();
    }

    @Test
    void anAmountMoreThanTwiceAnotherIsNotTheSameDebit() {
        assertThat(find(debit("2026-07-15", "PIX ENVIADO JOAO", "100.00"),
                debit("2026-08-15", "PIX ENVIADO JOAO", "250.00"),
                debit("2026-09-15", "PIX ENVIADO JOAO", "100.00"))).isEmpty();
    }

    @Test
    void aDebitOnAnotherDayIsNotPartOfTheSeries() {
        List<RecurringDebit> found = find(
                debit("2026-07-10", "INTERNET FIBRA", "119.90"),
                debit("2026-08-20", "INTERNET FIBRA", "119.90"),
                debit("2026-09-10", "INTERNET FIBRA", "119.90"),
                debit("2026-10-10", "INTERNET FIBRA", "119.90"));

        assertThat(found).singleElement().satisfies(debit -> assertThat(debit.months()).isEqualTo(3));
    }

    @Test
    void theLastDayOfAShortMonthIsTheSameDay() {
        List<RecurringDebit> found = find(
                debit("2026-07-31", "ESCOLA INFANTIL", "1200.00"),
                debit("2026-08-31", "ESCOLA INFANTIL", "1200.00"),
                debit("2026-09-30", "ESCOLA INFANTIL", "1200.00"));

        assertThat(found).singleElement().satisfies(debit -> {
            assertThat(debit.months()).isEqualTo(3);
            assertThat(debit.dayOfMonth()).isEqualTo(31);
        });
    }

    /** "PAGTO BOLETO" with the number dropped is the same key for the school and the gym. */
    @Test
    void twoBoletosWithTheSameDescriptionOnDifferentDaysAreTwoDebits() {
        List<RecurringDebit> found = find(
                debit("2026-07-10", "PAGTO BOLETO 1234", "450.00"),
                debit("2026-07-25", "PAGTO BOLETO 9876", "89.90"),
                debit("2026-08-10", "PAGTO BOLETO 1235", "450.00"),
                debit("2026-08-25", "PAGTO BOLETO 9877", "89.90"),
                debit("2026-09-10", "PAGTO BOLETO 1236", "450.00"),
                debit("2026-09-25", "PAGTO BOLETO 9878", "89.90"),
                debit("2026-10-10", "PAGTO BOLETO 1237", "450.00"));

        assertThat(found).extracting(RecurringDebit::dayOfMonth, RecurringDebit::amount, RecurringDebit::months)
                .containsExactly(
                        tuple(10, Money.of("450.00"), 4),
                        tuple(25, Money.of("89.90"), 3));
    }

    @Test
    void weeklyGroceriesAreAHabitNotABill() {
        List<AccountTransaction> saturdays = new ArrayList<>();
        for (LocalDate day = PERIOD.from(); !day.isAfter(PERIOD.to()); day = day.plusDays(1)) {
            if (DayOfWeek.SATURDAY.equals(day.getDayOfWeek())) {
                saturdays.add(debit(day.toString(), "SUPERMERCADO BOM PRECO", "250.00"));
            }
        }

        assertThat(RecurringDebits.find(saturdays, PERIOD, MIN_MONTHS)).isEmpty();
    }

    /** Paying the card bill every month is not a fixed expense: the purchases are on the card. */
    @Test
    void cardBillPaymentsInflowsAndDeletedDebitsAreLeftOut() {
        List<AccountTransaction> transactions = new ArrayList<>();
        for (String month : List.of("07", "08", "09", "10")) {
            transactions.add(debit("2026-" + month + "-10", "PAGAMENTO FATURA", "800.00", "Credit card payment"));
            AccountTransaction salary = debit("2026-" + month + "-05", "SALARIO", "3000.00");
            salary.setDirection(Direction.INFLOW);
            transactions.add(salary);
            AccountTransaction deleted = debit("2026-" + month + "-12", "SEGURO", "50.00");
            deleted.setDeletedAt(Instant.parse("2026-10-17T12:00:00Z"));
            transactions.add(deleted);
        }

        assertThat(RecurringDebits.find(transactions, PERIOD, MIN_MONTHS)).isEmpty();
    }

    @Test
    void debitsOutsideThePeriodDoNotCount() {
        assertThat(find(debit("2026-05-09", "CONDOMINIO", "690.00"),
                debit("2026-06-09", "CONDOMINIO", "690.00"),
                debit("2026-10-09", "CONDOMINIO", "690.00"))).isEmpty();
    }

    @Test
    void severalDebitsComeByDayOfTheMonth() {
        List<RecurringDebit> found = find(
                debit("2026-08-20", "PLANO DE SAUDE", "480.00"),
                debit("2026-08-09", "CONDOMINIO", "690.00"),
                debit("2026-09-20", "PLANO DE SAUDE", "480.00"),
                debit("2026-09-09", "CONDOMINIO", "690.00"),
                debit("2026-07-20", "PLANO DE SAUDE", "480.00"),
                debit("2026-07-09", "CONDOMINIO", "690.00"),
                debit("2026-10-09", "CONDOMINIO", "690.00"));

        assertThat(found).extracting(RecurringDebit::descriptionKey).containsExactly("condominio", "plano de saude");
    }

    private static List<RecurringDebit> find(AccountTransaction... transactions) {
        return RecurringDebits.find(List.of(transactions), PERIOD, MIN_MONTHS);
    }

    private static AccountTransaction debit(String date, String description, String amount) {
        return debit(date, description, amount, "Housing");
    }

    private static AccountTransaction debit(String date, String description, String amount, String category) {
        Instant now = Instant.parse("2026-10-18T12:00:00Z");
        return AccountTransaction.builder()
                .id(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerTransactionId(UUID.randomUUID().toString())
                .bookedOn(LocalDate.parse(date))
                .description(description)
                .amount(Money.of(amount))
                .direction(Direction.OUTFLOW)
                .status(TransactionStatus.POSTED)
                .category(category)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
