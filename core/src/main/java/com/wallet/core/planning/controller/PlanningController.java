package com.wallet.core.planning.controller;

import com.wallet.core.planning.dto.ExpectedSalaryRequest;
import com.wallet.core.planning.dto.ExpectedSalaryResponse;
import com.wallet.core.planning.dto.FixedExpenseRequest;
import com.wallet.core.planning.dto.FixedExpenseResponse;
import com.wallet.core.planning.dto.FixedExpenseSuggestionResponse;
import com.wallet.core.planning.dto.MonthPlanResponse;
import com.wallet.core.planning.service.FixedExpenseService;
import com.wallet.core.planning.service.FixedExpenseSuggestionService;
import com.wallet.core.planning.service.PlanningService;
import com.wallet.core.shared.security.CurrentUserId;
import com.wallet.core.shared.time.WalletTime;
import com.wallet.core.shared.web.Bulkheads;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.isNull;

@RestController
@RequestMapping("/internal/planning")
@RequiredArgsConstructor
public class PlanningController {

    private final PlanningService planningService;
    private final FixedExpenseService fixedExpenseService;
    private final FixedExpenseSuggestionService suggestionService;
    private final Bulkheads bulkheads;
    private final Clock clock;

    /**
     * {@code month=2026-10}; defaults to the current month. Reads the bills, their cycles and the
     * salary: behind the screens bulkhead.
     */
    @GetMapping("/month")
    public MonthPlanResponse month(@CurrentUserId UUID userId,
                                   @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        YearMonth planned = isNull(month) ? WalletTime.currentMonth(clock) : month;
        return bulkheads.screens().run(userId, () -> planningService.month(userId, planned));
    }

    @GetMapping("/fixed-expenses")
    public List<FixedExpenseResponse> fixedExpenses(@CurrentUserId UUID userId) {
        return fixedExpenseService.list(userId);
    }

    @PostMapping("/fixed-expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public FixedExpenseResponse createFixedExpense(@CurrentUserId UUID userId,
                                                   @Valid @RequestBody FixedExpenseRequest request) {
        return fixedExpenseService.create(userId, request);
    }

    @PutMapping("/fixed-expenses/{id}")
    public FixedExpenseResponse updateFixedExpense(@CurrentUserId UUID userId, @PathVariable UUID id,
                                                   @Valid @RequestBody FixedExpenseRequest request) {
        return fixedExpenseService.update(userId, id, request);
    }

    @DeleteMapping("/fixed-expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFixedExpense(@CurrentUserId UUID userId, @PathVariable UUID id) {
        fixedExpenseService.delete(userId, id);
    }

    /**
     * Debits that repeat every month in the bank accounts and are not fixed expenses yet. Decrypts
     * four months of descriptions: behind the screens bulkhead.
     */
    @GetMapping("/fixed-expense-suggestions")
    public List<FixedExpenseSuggestionResponse> fixedExpenseSuggestions(@CurrentUserId UUID userId) {
        return bulkheads.screens().run(userId, () -> suggestionService.list(userId));
    }

    /** Dismisses a suggestion for good. Adding one is creating the fixed expense, which covers it. */
    @DeleteMapping("/fixed-expense-suggestions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismissFixedExpenseSuggestion(@CurrentUserId UUID userId, @PathVariable String id) {
        suggestionService.dismiss(userId, id);
    }

    /** Replaces the prediction from the previous month while the month's salary has not landed. */
    @PutMapping("/expected-salary")
    public ExpectedSalaryResponse setExpectedSalary(@CurrentUserId UUID userId,
                                                    @Valid @RequestBody ExpectedSalaryRequest request) {
        return planningService.setExpectedSalary(userId, request);
    }

    @DeleteMapping("/expected-salary")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearExpectedSalary(@CurrentUserId UUID userId) {
        planningService.clearExpectedSalary(userId);
    }
}
