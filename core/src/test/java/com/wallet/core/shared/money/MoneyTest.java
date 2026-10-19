package com.wallet.core.shared.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void alwaysKeepsTwoDecimalPlaces() {
        assertThat(Money.of("10").toPlainString()).isEqualTo("10.00");
        assertThat(Money.of(new BigDecimal("7.5")).toPlainString()).isEqualTo("7.50");
    }

    @Test
    void roundsHalfToEven() {
        assertThat(Money.of("1.005").toPlainString()).isEqualTo("1.00");
        assertThat(Money.of("1.015").toPlainString()).isEqualTo("1.02");
    }

    @Test
    void sameValueWithDifferentScaleIsEqual() {
        assertThat(Money.of("1.0")).isEqualTo(Money.of("1.00"));
    }

    @Test
    void addsAndSubtractsWithoutLosingCents() {
        Money total = Money.of("10.10").plus(Money.of("0.25")).minus(Money.of("0.05"));

        assertThat(total).isEqualTo(Money.of("10.30"));
    }

    @Test
    void knowsItsSign() {
        assertThat(Money.of("-0.01").isNegative()).isTrue();
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.of("0.01").isPositive()).isTrue();
        assertThat(Money.of("5").negate()).isEqualTo(Money.of("-5"));
    }

    @Test
    void multipliesAndDividesToTheCent() {
        assertThat(Money.of("19.90").times(3)).isEqualTo(Money.of("59.70"));
        assertThat(Money.of("10.00").dividedBy(3)).isEqualTo(Money.of("3.33"));
        assertThat(Money.of("585.50").dividedBy(3)).isEqualTo(Money.of("195.17"));
        assertThat(Money.of("0.25").dividedBy(2)).isEqualTo(Money.of("0.12"));
        assertThat(Money.of("-4.83").abs()).isEqualTo(Money.of("4.83"));
        assertThatThrownBy(() -> Money.of("1.00").dividedBy(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingAmount() {
        assertThatThrownBy(() -> Money.of((String) null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of((BigDecimal) null)).isInstanceOf(IllegalArgumentException.class);
    }
}
