package com.wallet.core.insight.dto;

import com.wallet.core.shared.money.Money;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** The month's spending day by day, for the calendar. Days without spending are left out. */
public record DailySpendingResponse(YearMonth month, Money total, List<Day> days) {

    public record Day(LocalDate date, Money total, int count) {
    }
}
