package com.wallet.core.planning.service;

import com.wallet.core.banking.BankingQueries;
import com.wallet.core.planning.dto.ExpectedSalaryRequest;
import com.wallet.core.planning.dto.ExpectedSalaryResponse;
import com.wallet.core.planning.dto.MonthPlanResponse;
import com.wallet.core.planning.entity.PlanningSettings;
import com.wallet.core.planning.mapper.PlanningMapper;
import com.wallet.core.planning.repository.FixedExpenseRepository;
import com.wallet.core.planning.repository.PlanningSettingsRepository;
import com.wallet.core.shared.money.Money;
import com.wallet.core.shared.time.DateRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/** The month's plan from the synced data (salary, card bills) and what the user typed in. */
@Service
@RequiredArgsConstructor
public class PlanningService {

    /** How far back a salary is looked for, to predict one that has not landed yet. */
    static final int SALARY_LOOKBACK_MONTHS = 3;

    private final BankingQueries banking;
    private final FixedExpenseRepository fixedExpenses;
    private final PlanningSettingsRepository settings;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MonthPlanResponse month(UUID userId, YearMonth month) {
        Money expected = settings.findById(userId).map(PlanningSettings::getExpectedSalary).orElse(null);
        YearMonth firstLookedAt = month.minusMonths(SALARY_LOOKBACK_MONTHS);
        return MonthPlanner.plan(month,
                banking.salaryPayments(userId, new DateRange(month.atDay(1), month.atEndOfMonth())),
                banking.salaryPayments(userId, new DateRange(firstLookedAt.atDay(1), month.minusMonths(1).atEndOfMonth())),
                expected,
                banking.billsDueIn(userId, month),
                fixedExpenses.findByUserIdOrderByDayOfMonthAscNameAsc(userId));
    }

    @Transactional
    public ExpectedSalaryResponse setExpectedSalary(UUID userId, ExpectedSalaryRequest request) {
        Instant now = clock.instant();
        PlanningSettings current = settings.findById(userId).orElseGet(() -> PlanningMapper.newSettings(userId, now));
        current.setExpectedSalary(Money.of(request.amount()));
        current.setUpdatedAt(now);
        return new ExpectedSalaryResponse(settings.save(current).getExpectedSalary());
    }

    /** Back to predicting from the previous month. Nothing to do when nothing was set. */
    @Transactional
    public void clearExpectedSalary(UUID userId) {
        settings.findById(userId).ifPresent(current -> {
            current.setExpectedSalary(null);
            current.setUpdatedAt(clock.instant());
        });
    }
}
