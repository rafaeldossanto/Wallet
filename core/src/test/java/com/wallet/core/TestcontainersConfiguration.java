package com.wallet.core;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

/**
 * Real Postgres for the integration tests, the only place the core creates a container
 * ({@link TestCoreApplication} uses the local database).
 *
 * <p>Pinned to the same major version as the local database, so a test never behaves
 * differently just because a {@code latest} tag moved.
 *
 * <p>Docker lists the container as {@code wallet-core-test-db-<4 hex>}. The suffix keeps the name
 * free when two builds run at once (worktrees, or several cached test contexts). Nothing here is
 * started by hand: the tests create it and it goes away when they finish.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        String name = "wallet-core-test-db-" + UUID.randomUUID().toString().substring(0, 4);
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18"))
                .withCreateContainerCmdModifier(command -> {
                    command.withName(name);
                    // Like --rm: if Docker Desktop closes mid-run (Ryuk dies with it), the daemon
                    // deletes the container on its next start instead of leaving it behind.
                    command.getHostConfig().withAutoRemove(true);
                });
    }
}
