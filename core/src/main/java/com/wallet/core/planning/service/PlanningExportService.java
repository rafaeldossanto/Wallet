package com.wallet.core.planning.service;

import com.wallet.core.planning.PlanningExport;
import com.wallet.core.planning.PlanningExports;
import com.wallet.core.planning.entity.PlanningSettings;
import com.wallet.core.planning.repository.FixedExpenseRepository;
import com.wallet.core.planning.repository.PlanningSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class PlanningExportService implements PlanningExports {

    private final FixedExpenseRepository fixedExpenses;
    private final PlanningSettingsRepository settings;

    @Override
    @Transactional(readOnly = true)
    public PlanningExport export(UUID userId) {
        return new PlanningExport(
                settings.findById(userId).map(PlanningSettings::getExpectedSalary).orElse(null),
                fixedExpenses.findByUserIdOrderByDayOfMonthAscNameAsc(userId).stream()
                        .map(expense -> new PlanningExport.FixedExpense(expense.getId(), expense.getName(),
                                expense.getAmount(), expense.getDayOfMonth(), expense.getCategory()))
                        .toList());
    }
}
