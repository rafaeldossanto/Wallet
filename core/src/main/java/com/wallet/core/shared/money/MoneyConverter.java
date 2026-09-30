package com.wallet.core.shared.money;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

import static java.util.Objects.isNull;

/** Lets entities hold {@link Money} directly over a {@code NUMERIC(19,2)} column. */
@Converter(autoApply = true)
public class MoneyConverter implements AttributeConverter<Money, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(Money money) {
        return isNull(money) ? null : money.amount();
    }

    @Override
    public Money convertToEntityAttribute(BigDecimal value) {
        return isNull(value) ? null : Money.of(value);
    }
}
