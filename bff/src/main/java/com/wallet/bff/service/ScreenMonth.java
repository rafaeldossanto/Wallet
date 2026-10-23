package com.wallet.bff.service;

import com.wallet.bff.config.ClockConfig;
import com.wallet.bff.exception.BffException;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.YearMonth;

import static java.util.Objects.isNull;

/**
 * The {@code month} of the monthly screens (calendar, insights, planning). The controllers parse it
 * as {@code yyyy-MM}, the same strict rule as the core; here it is also held to the months the
 * Wallet can have data for. Each distinct month is a cache entry and a round of calls to the core,
 * so without a window one user could walk through thousands of empty months and push everyone
 * else's screens out of the cache.
 */
@UtilityClass
public class ScreenMonth {

    /** Ten years: Open Finance data in Brazil starts in 2021, and the Wallet reads one year back at the first sync. */
    static final int MONTHS_BACK = 120;

    /** The plan and the calendar may look ahead; a year covers any bill already scheduled. */
    static final int MONTHS_AHEAD = 12;

    /**
     * Null (the app did not say) stays null: the core takes the current month.
     *
     * @throws BffException 400 {@code request.invalid_parameter}, the code the core uses for a month that does not parse
     */
    public static String check(YearMonth month, Clock clock) {
        if (isNull(month)) {
            return null;
        }
        YearMonth current = YearMonth.from(ClockConfig.today(clock));
        if (month.isBefore(current.minusMonths(MONTHS_BACK)) || month.isAfter(current.plusMonths(MONTHS_AHEAD))) {
            throw new BffException(HttpStatus.BAD_REQUEST, "request.invalid_parameter",
                    "month must be within " + MONTHS_BACK + " months back and " + MONTHS_AHEAD + " ahead");
        }
        return month.toString();
    }
}
