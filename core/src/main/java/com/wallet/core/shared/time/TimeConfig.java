package com.wallet.core.shared.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The single source of "now". Services receive the {@link Clock} instead of calling
 * {@code LocalDate.now()}, so tests can freeze time (sync windows, lockouts, token expiry).
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
