package com.wallet.core.investment.dto;

import com.wallet.core.shared.finance.InvestmentKind;
import com.wallet.core.shared.money.Money;

import java.util.List;

/** Open positions only. {@code byKind} is sorted by total, largest first. */
public record PortfolioResponse(Money total, List<KindTotal> byKind, List<PositionResponse> positions) {

    public record KindTotal(InvestmentKind kind, Money total) {
    }
}
