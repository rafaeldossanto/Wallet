package com.wallet.core.planning.service;

import com.wallet.core.banking.BankingQueries;
import com.wallet.core.planning.dto.FixedExpenseSuggestionResponse;
import com.wallet.core.planning.entity.DismissedSuggestion;
import com.wallet.core.planning.mapper.PlanningMapper;
import com.wallet.core.planning.repository.DismissedSuggestionRepository;
import com.wallet.core.planning.repository.FixedExpenseRepository;
import com.wallet.core.shared.crypto.DataCipher;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import com.wallet.core.shared.time.DateRange;
import com.wallet.core.shared.time.WalletTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fixed expenses the bank statement suggests: debits that repeat every month, minus the ones the
 * user already registered or dismissed. Computed on every read; only the dismissals are kept.
 */
@Service
@RequiredArgsConstructor
public class FixedExpenseSuggestionService {

    /** The months looked at besides the current one. */
    static final int LOOKBACK_MONTHS = 3;

    /** In how many of those months (the current one included) a debit must show up. */
    static final int MIN_MONTHS = 3;

    private final BankingQueries banking;
    private final FixedExpenseRepository fixedExpenses;
    private final DismissedSuggestionRepository dismissals;
    private final DataCipher cipher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<FixedExpenseSuggestionResponse> list(UUID userId) {
        return suggestions(userId, dismissals.findByUserId(userId));
    }

    /**
     * It is not suggested again. Dismissing one already dismissed does nothing; an id that is not
     * among the user's suggestions (another user's, or the debit stopped) is not found.
     */
    @Transactional
    public void dismiss(UUID userId, String id) {
        SuggestionId suggestion = SuggestionId.parse(id).orElseThrow(FixedExpenseSuggestionService::notFound);
        List<DismissedSuggestion> dismissed = dismissals.findByUserId(userId);
        if (FixedExpenseSuggestions.isDismissed(suggestion, dismissed)) {
            return;
        }
        if (suggestions(userId, dismissed).stream().noneMatch(current -> current.id().equals(suggestion.value()))) {
            throw notFound();
        }
        dismissals.save(PlanningMapper.toDismissal(userId, suggestion.descriptionKey(), suggestion.dayOfMonth(),
                clock.instant()));
    }

    private List<FixedExpenseSuggestionResponse> suggestions(UUID userId, List<DismissedSuggestion> dismissed) {
        LocalDate today = WalletTime.today(clock);
        DateRange period = new DateRange(WalletTime.currentMonth(clock).minusMonths(LOOKBACK_MONTHS).atDay(1), today);
        return FixedExpenseSuggestions.select(banking.recurringDebits(userId, period, MIN_MONTHS),
                fixedExpenses.findByUserIdOrderByDayOfMonthAscNameAsc(userId), dismissed, cipher::fingerprint);
    }

    private static DomainException notFound() {
        return new DomainException(ErrorType.NOT_FOUND, "planning.fixed_expense_suggestion_not_found",
                "No such fixed expense suggestion");
    }
}
