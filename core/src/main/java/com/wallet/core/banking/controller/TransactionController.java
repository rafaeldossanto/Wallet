package com.wallet.core.banking.controller;

import com.wallet.core.banking.dto.TransactionResponse;
import com.wallet.core.banking.repository.TransactionFilter;
import com.wallet.core.banking.service.BankingReadService;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.security.CurrentUserId;
import com.wallet.core.shared.time.DateRange;
import com.wallet.core.shared.time.WalletTime;
import com.wallet.core.shared.web.PageQuery;
import com.wallet.core.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TransactionController {

    /** Also bounds the in-memory text search. */
    private static final int MAX_PERIOD_DAYS = 366;

    private final BankingReadService bankingReadService;
    private final Clock clock;

    /** Defaults to the current month so far. */
    @GetMapping("/internal/transactions")
    public PageResponse<TransactionResponse> search(
            @CurrentUserId UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) Direction direction,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        LocalDate today = WalletTime.today(clock);
        DateRange period = DateRange.of(from, to, today.withDayOfMonth(1), today, MAX_PERIOD_DAYS);
        return bankingReadService.searchTransactions(new TransactionFilter(userId, period, accountId, direction), q,
                PageQuery.of(page, pageSize));
    }
}
