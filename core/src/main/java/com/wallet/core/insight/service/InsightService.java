package com.wallet.core.insight.service;

import com.wallet.core.banking.AccountSummary;
import com.wallet.core.banking.BalancePoint;
import com.wallet.core.banking.BankingQueries;
import com.wallet.core.banking.CashFlow;
import com.wallet.core.connection.ConnectionRegistry;
import com.wallet.core.connection.LinkedConnection;
import com.wallet.core.insight.dto.DailySpendingResponse;
import com.wallet.core.insight.dto.NetWorthResponse;
import com.wallet.core.insight.dto.OverviewResponse;
import com.wallet.core.insight.dto.SpendingResponse;
import com.wallet.core.investment.InvestmentQueries;
import com.wallet.core.investment.PositionSummary;
import com.wallet.core.shared.money.Money;
import com.wallet.core.shared.time.DateRange;
import com.wallet.core.shared.time.WalletTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Answers built from several modules. Owns no data. */
@Service
@RequiredArgsConstructor
public class InsightService {

    private final BankingQueries banking;
    private final InvestmentQueries investments;
    private final ConnectionRegistry connections;
    private final Clock clock;

    public OverviewResponse overview(UUID userId) {
        List<AccountSummary> accounts = banking.accounts(userId);
        Money cash = sum(accounts.stream().filter(account -> !account.kind().isCreditCard()).map(AccountSummary::balance).toList());
        Money debt = sum(accounts.stream().filter(account -> account.kind().isCreditCard()).map(AccountSummary::balance).toList());
        Money invested = sum(investments.openPositions(userId).stream().map(PositionSummary::balance).toList());

        LocalDate today = WalletTime.today(clock);
        YearMonth month = YearMonth.from(today);
        CashFlow flow = banking.cashFlow(userId, new DateRange(month.atDay(1), today));

        Instant lastSyncedAt = connections.findByUser(userId).stream()
                .map(LinkedConnection::lastSyncedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new OverviewResponse(cash.plus(invested).minus(debt), cash, debt, invested, month,
                flow.income(), flow.expenses(), lastSyncedAt);
    }

    public SpendingResponse spending(UUID userId, YearMonth month) {
        List<SpendingResponse.CategoryTotal> categories = banking
                .spendingByCategory(userId, new DateRange(month.atDay(1), month.atEndOfMonth())).stream()
                .map(spending -> new SpendingResponse.CategoryTotal(spending.category(), spending.total()))
                .toList();
        return new SpendingResponse(month, sum(categories.stream().map(SpendingResponse.CategoryTotal::total).toList()),
                categories);
    }

    public DailySpendingResponse dailySpending(UUID userId, YearMonth month) {
        List<DailySpendingResponse.Day> days = banking
                .spendingByDay(userId, new DateRange(month.atDay(1), month.atEndOfMonth())).stream()
                .map(day -> new DailySpendingResponse.Day(day.date(), day.total(), day.count()))
                .toList();
        return new DailySpendingResponse(month, sum(days.stream().map(DailySpendingResponse.Day::total).toList()), days);
    }

    public NetWorthResponse netWorth(UUID userId, DateRange period) {
        Map<LocalDate, Money> invested = investments.totalHistory(userId, period);
        List<NetWorthResponse.Point> points = banking.balanceHistory(userId, period).stream()
                .map(balance -> point(balance, invested.getOrDefault(balance.date(), Money.ZERO)))
                .toList();
        return new NetWorthResponse(points);
    }

    private static NetWorthResponse.Point point(BalancePoint balance, Money invested) {
        Money netWorth = balance.cash().plus(invested).minus(balance.creditCardDebt());
        return new NetWorthResponse.Point(balance.date(), netWorth, balance.cash(), invested, balance.creditCardDebt());
    }

    private static Money sum(List<Money> values) {
        return values.stream().reduce(Money.ZERO, Money::plus);
    }
}
