package com.wallet.core.planning.service;

import com.wallet.core.planning.dto.FixedExpenseRequest;
import com.wallet.core.planning.dto.FixedExpenseResponse;
import com.wallet.core.planning.entity.FixedExpense;
import com.wallet.core.planning.mapper.PlanningMapper;
import com.wallet.core.planning.repository.FixedExpenseRepository;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** The user's fixed expenses: entered once, they count in every month's plan. */
@Service
@RequiredArgsConstructor
public class FixedExpenseService {

    /** Far beyond anyone's real list; it only keeps a runaway client from filling the table. */
    static final int MAX_PER_USER = 100;

    private final FixedExpenseRepository repository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<FixedExpenseResponse> list(UUID userId) {
        return repository.findByUserIdOrderByDayOfMonthAscNameAsc(userId).stream().map(PlanningMapper::toResponse).toList();
    }

    @Transactional
    public FixedExpenseResponse create(UUID userId, FixedExpenseRequest request) {
        if (repository.countByUserId(userId) >= MAX_PER_USER) {
            throw new DomainException(ErrorType.UNPROCESSABLE, "planning.fixed_expense_limit",
                    "At most " + MAX_PER_USER + " fixed expenses");
        }
        return PlanningMapper.toResponse(repository.save(PlanningMapper.toEntity(userId, request, clock.instant())));
    }

    @Transactional
    public FixedExpenseResponse update(UUID userId, UUID id, FixedExpenseRequest request) {
        FixedExpense expense = find(userId, id);
        PlanningMapper.copy(request, expense, clock.instant());
        return PlanningMapper.toResponse(expense);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        repository.delete(find(userId, id));
    }

    /** Another user's expense is as missing as one that never existed. */
    private FixedExpense find(UUID userId, UUID id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new DomainException(ErrorType.NOT_FOUND, "planning.fixed_expense_not_found",
                        "No such fixed expense"));
    }
}
