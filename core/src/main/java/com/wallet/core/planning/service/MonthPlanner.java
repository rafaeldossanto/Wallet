package com.wallet.core.planning.service;

import com.wallet.core.banking.CardBill;
import com.wallet.core.banking.CardBillItem;
import com.wallet.core.banking.SalaryPayment;
import com.wallet.core.planning.dto.MonthPlanResponse;
import com.wallet.core.planning.dto.MonthPlanResponse.SalarySource;
import com.wallet.core.planning.entity.FixedExpense;
import com.wallet.core.planning.mapper.PlanningMapper;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.money.Money;
import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/** The month's arithmetic, on data already read. Owns the rules; reads nothing. */
@UtilityClass
class MonthPlanner {

    /**
     * @param received   the month's salary deposits, oldest first
     * @param earlier    salary deposits of the months before, oldest first: the prediction
     *                   comes from the latest month among them
     * @param expected   the salary the user set, or null
     */
    MonthPlanResponse plan(YearMonth month, List<SalaryPayment> received, List<SalaryPayment> earlier, Money expected,
                           List<CardBill> cardBills, List<FixedExpense> fixedExpenses) {
        MonthPlanResponse.Salary salary = salary(month, received, earlier, expected);
        List<MonthPlanResponse.Bill> bills = cardBills.stream()
                .map(bill -> PlanningMapper.toBill(bill, bill.total().minus(net(bill.items()))))
                .toList();
        List<MonthPlanResponse.FixedExpenseItem> fixed = fixedExpenses.stream()
                .map(expense -> PlanningMapper.toMonthItem(expense, month))
                .toList();

        Money salaryTotal = isNull(salary.amount()) ? Money.ZERO : salary.amount();
        Money billsTotal = bills.stream().map(MonthPlanResponse.Bill::total).reduce(Money.ZERO, Money::plus);
        Money fixedTotal = fixed.stream().map(MonthPlanResponse.FixedExpenseItem::amount).reduce(Money.ZERO, Money::plus);
        Money leftover = salaryTotal.minus(billsTotal).minus(fixedTotal);
        return new MonthPlanResponse(month, salary, bills, fixed,
                new MonthPlanResponse.Totals(salaryTotal, billsTotal, fixedTotal, leftover));
    }

    /**
     * What landed in the month; until it lands, the salary the user set or else the latest month's
     * salary, expected on the same day of the month.
     */
    MonthPlanResponse.Salary salary(YearMonth month, List<SalaryPayment> received, List<SalaryPayment> earlier,
                                    Money expected) {
        if (!received.isEmpty()) {
            return new MonthPlanResponse.Salary(sum(received), received.getLast().date(), false, SalarySource.RECEIVED,
                    expected);
        }
        Money previous = null;
        LocalDate expectedOn = null;
        if (!earlier.isEmpty()) {
            YearMonth latest = YearMonth.from(earlier.getLast().date());
            List<SalaryPayment> latestMonth = earlier.stream()
                    .filter(payment -> YearMonth.from(payment.date()).equals(latest))
                    .toList();
            previous = sum(latestMonth);
            expectedOn = month.atDay(Math.min(latestMonth.getLast().date().getDayOfMonth(), month.lengthOfMonth()));
        }
        if (nonNull(expected)) {
            return new MonthPlanResponse.Salary(expected, expectedOn, true, SalarySource.EXPECTED, expected);
        }
        if (nonNull(previous)) {
            return new MonthPlanResponse.Salary(previous, expectedOn, true, SalarySource.PREVIOUS_MONTH, null);
        }
        return new MonthPlanResponse.Salary(null, null, false, SalarySource.NONE, null);
    }

    /** Purchases minus refunds. */
    private static Money net(List<CardBillItem> items) {
        Money net = Money.ZERO;
        for (CardBillItem item : items) {
            net = Direction.INFLOW.equals(item.direction()) ? net.minus(item.amount()) : net.plus(item.amount());
        }
        return net;
    }

    private static Money sum(List<SalaryPayment> payments) {
        return payments.stream().map(SalaryPayment::amount).reduce(Money.ZERO, Money::plus);
    }
}
