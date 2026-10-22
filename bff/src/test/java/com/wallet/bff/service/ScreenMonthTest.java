package com.wallet.bff.service;

import com.wallet.bff.exception.BffException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScreenMonthTest {

    /** 2026-10-01 01:00 UTC is still September 30 in São Paulo. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void noMonthStaysForTheCoreToDefault() {
        assertThat(ScreenMonth.check(null, CLOCK)).isNull();
    }

    @Test
    void tenYearsBackAndOneAheadOfTheBrazilianMonthAreAccepted() {
        assertThat(ScreenMonth.check(YearMonth.of(2016, 9), CLOCK)).isEqualTo("2016-09");
        assertThat(ScreenMonth.check(YearMonth.of(2027, 9), CLOCK)).isEqualTo("2027-09");
    }

    @Test
    void anythingFurtherIsRefused() {
        assertThatThrownBy(() -> ScreenMonth.check(YearMonth.of(2016, 8), CLOCK))
                .isInstanceOfSatisfying(BffException.class,
                        refused -> assertThat(refused.getCode()).isEqualTo("request.invalid_parameter"));
        assertThatThrownBy(() -> ScreenMonth.check(YearMonth.of(2027, 10), CLOCK)).isInstanceOf(BffException.class);
    }
}
