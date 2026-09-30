package com.wallet.core.shared.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import java.math.BigDecimal;

/**
 * Writes {@link BigDecimal} as a JSON string without exponent ({@code "1000.00"}, never
 * {@code 1E+3}). As a JSON number the app would read it as a {@code double} and lose cents.
 */
public class BigDecimalPlainStringSerializer extends StdSerializer<BigDecimal> {

    public BigDecimalPlainStringSerializer() {
        super(BigDecimal.class);
    }

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
        gen.writeString(value.toPlainString());
    }
}
