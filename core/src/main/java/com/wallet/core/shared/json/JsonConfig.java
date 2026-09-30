package com.wallet.core.shared.json;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;

@Configuration(proxyBeanMethods = false)
public class JsonConfig {

    @Bean
    JsonMapperBuilderCustomizer bigDecimalAsString() {
        return builder -> builder.addModule(decimalModule());
    }

    public static SimpleModule decimalModule() {
        return new SimpleModule("wallet-decimal")
                .addSerializer(BigDecimal.class, new BigDecimalPlainStringSerializer());
    }
}
