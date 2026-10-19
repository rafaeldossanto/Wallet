package com.wallet.bff.controller;

import com.wallet.bff.auth.CurrentUserId;
import com.wallet.bff.model.dto.request.ExpectedSalaryRequest;
import com.wallet.bff.model.dto.request.FixedExpenseRequest;
import com.wallet.bff.model.dto.response.ExpectedSalaryResponse;
import com.wallet.bff.model.dto.response.FixedExpenseResponse;
import com.wallet.bff.model.dto.response.FixedExpenseSuggestionResponse;
import com.wallet.bff.model.dto.response.PlanningResponse;
import com.wallet.bff.service.PlanningService;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/planning")
@RequiredArgsConstructor
public class PlanningController {

    private final PlanningService planningService;

    /** {@code month=2026-10}; the core validates it and defaults to the current month. */
    @GetMapping
    public PlanningResponse month(@CurrentUserId UUID userId, @RequestParam(required = false) String month) {
        return planningService.month(userId, month);
    }

    @GetMapping("/fixed-expenses")
    public List<FixedExpenseResponse> fixedExpenses() {
        return planningService.fixedExpenses();
    }

    @PostMapping("/fixed-expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public FixedExpenseResponse createFixedExpense(@CurrentUserId UUID userId, @RequestBody FixedExpenseRequest request) {
        return planningService.createFixedExpense(userId, request);
    }

    @PutMapping("/fixed-expenses/{id}")
    public FixedExpenseResponse updateFixedExpense(@CurrentUserId UUID userId, @PathVariable UUID id,
                                                   @RequestBody FixedExpenseRequest request) {
        return planningService.updateFixedExpense(userId, id, request);
    }

    @DeleteMapping("/fixed-expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFixedExpense(@CurrentUserId UUID userId, @PathVariable UUID id) {
        planningService.deleteFixedExpense(userId, id);
    }

    /** Debits that repeat every month in the bank accounts and are not fixed expenses yet. */
    @GetMapping("/fixed-expense-suggestions")
    public List<FixedExpenseSuggestionResponse> fixedExpenseSuggestions() {
        return planningService.fixedExpenseSuggestions();
    }

    /** It is not suggested again. Adding one is creating the fixed expense. */
    @DeleteMapping("/fixed-expense-suggestions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismissFixedExpenseSuggestion(@PathVariable String id) {
        planningService.dismissFixedExpenseSuggestion(id);
    }

    @PutMapping("/expected-salary")
    public ExpectedSalaryResponse setExpectedSalary(@CurrentUserId UUID userId, @RequestBody ExpectedSalaryRequest request) {
        return planningService.setExpectedSalary(userId, request);
    }

    /** Back to the prediction from the previous month's salary. */
    @DeleteMapping("/expected-salary")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearExpectedSalary(@CurrentUserId UUID userId) {
        planningService.clearExpectedSalary(userId);
    }
}
