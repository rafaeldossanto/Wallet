package com.wallet.core.insight.dto;

import com.wallet.core.shared.money.Money;

import java.time.YearMonth;
import java.util.List;

public record SpendingResponse(YearMonth month, Money total, List<CategoryTotal> categories) {

    public record CategoryTotal(String category, Money total) {
    }
}
