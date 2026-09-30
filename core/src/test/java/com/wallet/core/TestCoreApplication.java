package com.wallet.core;

import org.springframework.boot.SpringApplication;

/**
 * Runs the core against a throwaway Postgres in Docker, without touching the local database:
 * {@code ./mvnw spring-boot:test-run}.
 */
public class TestCoreApplication {

    public static void main(String[] args) {
        SpringApplication.from(CoreApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
