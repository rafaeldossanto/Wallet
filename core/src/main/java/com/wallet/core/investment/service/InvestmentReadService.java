package com.wallet.core.investment.service;

import com.wallet.core.investment.InvestmentQueries;
import com.wallet.core.investment.PositionSummary;
import com.wallet.core.investment.dto.PortfolioResponse;
import com.wallet.core.investment.entity.Investment;
import com.wallet.core.investment.mapper.InvestmentReadMapper;
import com.wallet.core.investment.repository.InvestmentAggregates;
import com.wallet.core.investment.repository.InvestmentRepository;
import com.wallet.core.shared.finance.InvestmentKind;
import com.wallet.core.shared.money.Money;
import com.wallet.core.shared.time.DateRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvestmentReadService implements InvestmentQueries {

    private final InvestmentRepository repository;
    private final InvestmentAggregates aggregates;

    @Transactional(readOnly = true)
    public PortfolioResponse portfolio(UUID userId) {
        List<Investment> open = repository.findByUserIdAndClosedAtIsNullOrderByKindAscNameAsc(userId);
        Map<InvestmentKind, Money> byKind = new EnumMap<>(InvestmentKind.class);
        Money total = Money.ZERO;
        for (Investment investment : open) {
            byKind.merge(investment.getKind(), investment.getBalance(), Money::plus);
            total = total.plus(investment.getBalance());
        }
        List<PortfolioResponse.KindTotal> kinds = byKind.entrySet().stream()
                .map(entry -> new PortfolioResponse.KindTotal(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(PortfolioResponse.KindTotal::total).reversed())
                .toList();
        return new PortfolioResponse(total, kinds, open.stream().map(InvestmentReadMapper::toResponse).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PositionSummary> openPositions(UUID userId) {
        return repository.findByUserIdAndClosedAtIsNullOrderByKindAscNameAsc(userId).stream()
                .map(InvestmentReadMapper::toSummary)
                .toList();
    }

    @Override
    public Map<LocalDate, Money> totalHistory(UUID userId, DateRange period) {
        List<InvestmentAggregates.SnapshotRow> rows = aggregates.snapshotsUpTo(userId, period.to());
        Map<UUID, Money> latestPerPosition = new HashMap<>();
        Map<LocalDate, Money> totals = new LinkedHashMap<>();
        int next = 0;
        for (LocalDate day : period.days()) {
            while (next < rows.size() && !rows.get(next).date().isAfter(day)) {
                latestPerPosition.put(rows.get(next).investmentId(), rows.get(next).balance());
                next++;
            }
            totals.put(day, latestPerPosition.values().stream().reduce(Money.ZERO, Money::plus));
        }
        return totals;
    }
}
