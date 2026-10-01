package com.wallet.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** The user's calendar, same as the core's: periods sent to the core are Brazilian dates. */
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
