package com.wallet.core.shared.time;

import lombok.experimental.UtilityClass;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * "Today" and "this month" in the user's calendar. Instants are UTC everywhere; calendar dates
 * (a transaction's day, a bill's due date, the daily balance) are Brazilian dates.
 */
@UtilityClass
public class WalletTime {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    public LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    public YearMonth currentMonth(Clock clock) {
        return YearMonth.now(clock.withZone(ZONE));
    }
}
