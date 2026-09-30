package com.wallet.core.investment;

import com.wallet.core.shared.finance.InvestmentKind;
import com.wallet.core.shared.money.Money;

import java.time.LocalDate;
import java.util.UUID;

public record PositionSummary(
        UUID id,
        UUID connectionId,
        InvestmentKind kind,
        String subtype,
        String name,
        String currencyCode,
        Money balance,
        Money amountInvested,
        LocalDate dueDate) {
}
