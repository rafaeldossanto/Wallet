package com.wallet.core;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

/** Replaces the system clock in the whole application context, JWT validation included. */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

    public static final Instant START = Instant.parse("2026-10-01T12:00:00Z");

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock(START);
    }
}
