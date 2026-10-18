package com.wallet.core.planning.service;

import com.wallet.core.banking.CardBill;
import com.wallet.core.banking.CardBillItem;
import com.wallet.core.banking.SalaryPayment;
import com.wallet.core.planning.dto.MonthPlanResponse;
import com.wallet.core.planning.dto.MonthPlanResponse.SalarySource;
import com.wallet.core.planning.entity.FixedExpense;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The month's rules, on made-up numbers. */
class MonthPlannerTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    @Test
    void theSalaryIsWhatLandedInTheMonth() {
        MonthPlanResponse.Salary salary = MonthPlanner.salary(OCTOBER,
                List.of(payment("2026-10-05", "3100.00")), List.of(payment("2026-09-05", "3000.00")), null);

        assertThat(salary).isEqualTo(new MonthPlanResponse.Salary(Money.of("3100.00"), LocalDate.parse("2026-10-05"),
                false, SalarySource.RECEIVED, null));
    }

    /** An advance and the rest: Open Finance sees two deposits, the salary is both. */
    @Test
    void twoDepositsInTheMonthAddUp() {
        MonthPlanResponse.Salary salary = MonthPlanner.salary(OCTOBER,
                List.of(payment("2026-10-05", "1800.00"), payment("2026-10-20", "1200.00")), List.of(), null);

        assertThat(salary.amount()).isEqualTo(Money.of("3000.00"));
        assertThat(salary.date()).isEqualTo(LocalDate.parse("2026-10-20"));
    }

    @Test
    void untilItLandsTheLastMonthsSalaryIsThePrediction() {
        MonthPlanResponse.Salary salary = MonthPlanner.salary(OCTOBER, List.of(),
                List.of(payment("2026-08-05", "2900.00"), payment("2026-09-04", "3000.00")), null);

        assertThat(salary).isEqualTo(new MonthPlanResponse.Salary(Money.of("3000.00"), LocalDate.parse("2026-10-04"),
                true, SalarySource.PREVIOUS_MONTH, null));
    }

    @Test
    void aPaydayOnThe31stIsExpectedOnTheLastDayOfAShortMonth() {
        MonthPlanResponse.Salary salary = MonthPlanner.salary(YearMonth.of(2026, 2), List.of(),
                List.of(payment("2026-01-31", "3000.00")), null);

        assertThat(salary.date()).isEqualTo(LocalDate.parse("2026-02-28"));
    }

    @Test
    void theSalaryTheUserSetReplacesThePrediction() {
        MonthPlanResponse.Salary salary = MonthPlanner.salary(OCTOBER, List.of(),
                List.of(payment("2026-09-05", "3000.00")), Money.of("3250.00"));

        assertThat(salary).isEqualTo(new MonthPlanResponse.Salary(Money.of("3250.00"), LocalDate.parse("2026-10-05"),
                true, SalarySource.EXPECTED, Money.of("3250.00")));
    }

    @Test
    void butNotTheSalaryThatLanded() {
        MonthPlanResponse.Salary salary = MonthPlanner.salary(OCTOBER, List.of(payment("2026-10-05", "3100.00")),
                List.of(), Money.of("3250.00"));

        assertThat(salary.amount()).isEqualTo(Money.of("3100.00"));
        assertThat(salary.source()).isEqualTo(SalarySource.RECEIVED);
        assertThat(salary.expectedAmount()).isEqualTo(Money.of("3250.00"));
    }

    @Test
    void withNothingToGoByThereIsNoSalary() {
        MonthPlanResponse.Salary salary = MonthPlanner.salary(OCTOBER, List.of(), List.of(), null);

        assertThat(salary).isEqualTo(new MonthPlanResponse.Salary(null, null, false, SalarySource.NONE, null));
    }

    @Test
    void whatIsLeftIsTheSalaryMinusTheBillsMinusTheFixedExpenses() {
        MonthPlanResponse plan = MonthPlanner.plan(OCTOBER, List.of(payment("2026-10-05", "3000.00")), List.of(), null,
                List.of(bill("640.00", false, item("600.00", Direction.OUTFLOW)), bill("180.50", false, item("180.50", Direction.OUTFLOW))),
                List.of(fixed("Faculdade", "450.00", 10), fixed("Internet", "99.90", 15)));

        assertThat(plan.totals()).isEqualTo(new MonthPlanResponse.Totals(Money.of("3000.00"), Money.of("820.50"),
                Money.of("549.90"), Money.of("1629.60")));
    }

    @Test
    void theMonthCanEndInTheRed() {
        MonthPlanResponse plan = MonthPlanner.plan(OCTOBER, List.of(), List.of(), null,
                List.of(bill("640.00", false, item("640.00", Direction.OUTFLOW))), List.of(fixed("Aluguel", "1200.00", 5)));

        assertThat(plan.salary().source()).isEqualTo(SalarySource.NONE);
        assertThat(plan.totals().leftover()).isEqualTo(Money.of("-1840.00"));
    }

    /** The bank's total stands; what the listed purchases do not explain shows up as other charges. */
    @Test
    void theBillTotalIsTheBanksAndTheGapIsOtherCharges() {
        MonthPlanResponse plan = MonthPlanner.plan(OCTOBER, List.of(), List.of(), null, List.of(
                bill("652.40", false, item("400.00", Direction.OUTFLOW), item("240.00", Direction.OUTFLOW)),
                bill("230.00", false, item("250.00", Direction.OUTFLOW), item("30.00", Direction.INFLOW)),
                bill("75.00", false, item("75.00", Direction.OUTFLOW))), List.of());

        assertThat(plan.bills()).extracting(MonthPlanResponse.Bill::otherCharges)
                .containsExactly(Money.of("12.40"), Money.of("10.00"), Money.ZERO);
        assertThat(plan.totals().bills()).isEqualTo(Money.of("957.40"));
    }

    @Test
    void aFixedExpenseOnThe31stFallsOnTheLastDayOfTheMonth() {
        MonthPlanResponse plan = MonthPlanner.plan(YearMonth.of(2026, 11), List.of(), List.of(), null, List.of(),
                List.of(fixed("Aluguel", "1200.00", 31)));

        assertThat(plan.fixedExpenses().getFirst().date()).isEqualTo(LocalDate.parse("2026-11-30"));
        assertThat(plan.fixedExpenses().getFirst().dayOfMonth()).isEqualTo(31);
    }

    private static SalaryPayment payment(String date, String amount) {
        return new SalaryPayment(LocalDate.parse(date), Money.of(amount));
    }

    private static CardBill bill(String total, boolean open, CardBillItem... items) {
        return new CardBill(UUID.randomUUID(), UUID.randomUUID(), "Cartão", "1234", LocalDate.parse("2026-10-10"),
                LocalDate.parse("2026-10-03"), Money.of(total), open, List.of(items));
    }

    private static CardBillItem item(String amount, Direction direction) {
        return new CardBillItem(UUID.randomUUID(), LocalDate.parse("2026-09-20"), "Compra", Money.of(amount), direction,
                "Shopping", null, null);
    }

    private static FixedExpense fixed(String name, String amount, int dayOfMonth) {
        return FixedExpense.builder().id(UUID.randomUUID()).name(name).amount(Money.of(amount)).dayOfMonth(dayOfMonth).build();
    }
}
