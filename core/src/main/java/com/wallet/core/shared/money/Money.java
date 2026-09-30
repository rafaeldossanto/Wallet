package com.wallet.core.shared.money;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static java.util.Objects.isNull;

/**
 * An amount of money with two decimal places. The currency lives on the account, not here.
 *
 * <p>Never built from a {@code double}: it travels as a string in JSON and as
 * {@code NUMERIC(19,2)} in the database, so cents are never rounded away.
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        if (isNull(amount)) {
            throw new IllegalArgumentException("amount is required");
        }
        amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    @JsonCreator
    public static Money of(String amount) {
        if (isNull(amount)) {
            throw new IllegalArgumentException("amount is required");
        }
        return new Money(new BigDecimal(amount));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money negate() {
        return new Money(amount.negate());
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @JsonValue
    public String toPlainString() {
        return amount.toPlainString();
    }

    @Override
    public String toString() {
        return toPlainString();
    }
}
