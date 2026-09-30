package com.wallet.core.insight.controller;

import com.wallet.core.insight.dto.NetWorthResponse;
import com.wallet.core.insight.dto.OverviewResponse;
import com.wallet.core.insight.dto.SpendingResponse;
import com.wallet.core.insight.service.InsightService;
import com.wallet.core.shared.security.CurrentUserId;
import com.wallet.core.shared.time.DateRange;
import com.wallet.core.shared.time.WalletTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static java.util.Objects.isNull;

@RestController
@RequiredArgsConstructor
public class InsightController {

    private static final int NET_WORTH_DEFAULT_DAYS = 30;
    private static final int NET_WORTH_MAX_DAYS = 731;

    private final InsightService insightService;
    private final Clock clock;

    @GetMapping("/internal/overview")
    public OverviewResponse overview(@CurrentUserId UUID userId) {
        return insightService.overview(userId);
    }

    /** {@code month=2026-09}; defaults to the current month. */
    @GetMapping("/internal/insights/spending-by-category")
    public SpendingResponse spending(@CurrentUserId UUID userId,
                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return insightService.spending(userId, isNull(month) ? WalletTime.currentMonth(clock) : month);
    }

    /** Defaults to the last 30 days. */
    @GetMapping("/internal/insights/net-worth")
    public NetWorthResponse netWorth(
            @CurrentUserId UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = WalletTime.today(clock);
        DateRange period = DateRange.of(from, to, today.minusDays(NET_WORTH_DEFAULT_DAYS - 1), today, NET_WORTH_MAX_DAYS);
        return insightService.netWorth(userId, period);
    }
}
