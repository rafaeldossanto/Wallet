package com.wallet.core.banking.controller;

import com.wallet.core.banking.dto.TransactionResponse;
import com.wallet.core.banking.repository.TransactionFilter;
import com.wallet.core.banking.service.BankingReadService;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.security.CurrentUserId;
import com.wallet.core.shared.time.DateRange;
import com.wallet.core.shared.time.WalletTime;
import com.wallet.core.shared.web.PageQuery;
import com.wallet.core.shared.web.Bulkheads;
import com.wallet.core.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.util.StringUtils.hasText;

@RestController
@RequiredArgsConstructor
public class TransactionController {

    private static final int MAX_PERIOD_DAYS = 366;

    /**
     * A text search decrypts every description of the period, so it covers less: a quarter. The
     * app searches one month at a time.
     */
    private static final int MAX_SEARCH_PERIOD_DAYS = 93;

    private final BankingReadService bankingReadService;
    private final Bulkheads bulkheads;
    private final Clock clock;

    /**
     * Defaults to the current month so far. {@code spending=true} keeps only what counts as
     * spending, by the same rule as the spending insights, so a day's list adds up to the
     * calendar's total for that day. With {@code q} the period is shorter and the search goes
     * through the search bulkhead.
     */
    @GetMapping("/internal/transactions")
    public PageResponse<TransactionResponse> search(
            @CurrentUserId UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) Direction direction,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean spending,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        LocalDate today = WalletTime.today(clock);
        boolean textSearch = hasText(q);
        DateRange period = DateRange.of(from, to, today.withDayOfMonth(1), today,
                textSearch ? MAX_SEARCH_PERIOD_DAYS : MAX_PERIOD_DAYS);
        TransactionFilter filter = new TransactionFilter(userId, period, accountId, direction, spending);
        PageQuery pageQuery = PageQuery.of(page, pageSize);
        if (!textSearch) {
            return bankingReadService.listTransactions(filter, pageQuery);
        }
        return bulkheads.search().run(userId, () -> bankingReadService.searchTransactions(filter, q, pageQuery));
    }
}
