package com.wallet.core.insight.dto;

import com.wallet.core.shared.money.Money;

import java.time.LocalDate;
import java.util.List;

/** One point per day. Days before the first sync are zero: the Open Finance has no balance history. */
public record NetWorthResponse(List<Point> points) {

    public record Point(LocalDate date, Money netWorth, Money cash, Money investments, Money creditCardDebt) {
    }
}
