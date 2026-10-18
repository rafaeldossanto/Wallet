package com.wallet.core.planning.mapper;

import com.wallet.core.banking.CardBill;
import com.wallet.core.banking.CardBillItem;
import com.wallet.core.planning.dto.FixedExpenseRequest;
import com.wallet.core.planning.dto.FixedExpenseResponse;
import com.wallet.core.planning.dto.MonthPlanResponse;
import com.wallet.core.planning.entity.FixedExpense;
import com.wallet.core.planning.entity.PlanningSettings;
import com.wallet.core.shared.money.Money;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import static org.springframework.util.StringUtils.hasText;

@UtilityClass
public class PlanningMapper {

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

    public PlanningSettings newSettings(UUID userId, Instant now) {
        return PlanningSettings.builder().userId(userId).createdAt(now).updatedAt(now).build();
    }
}
