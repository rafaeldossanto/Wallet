package com.wallet.core.shared.json;

import com.wallet.core.shared.money.Money;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BigDecimalPlainStringSerializerTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(JsonConfig.decimalModule())
            .build();

    @Test
    void writesBigDecimalAsPlainString() {
        String json = mapper.writeValueAsString(Map.of("value", new BigDecimal("1E+3")));

        assertThat(json).isEqualTo("{\"value\":\"1000\"}");
    }

    @Test
    void writesMoneyAsStringWithCents() {
        assertThat(mapper.writeValueAsString(Money.of("12.5"))).isEqualTo("\"12.50\"");
    }

    @Test
    void readsMoneyFromString() {
        assertThat(mapper.readValue("\"12.345\"", Money.class)).isEqualTo(Money.of("12.34"));
    }
}
