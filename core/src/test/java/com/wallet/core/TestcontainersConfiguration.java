package com.wallet.core;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

/**
 * Real Postgres for integration tests and for {@link TestCoreApplication}.
 *
 * <p>Pinned to the same major version as the local database, so a test never behaves
 * differently just because a {@code latest} tag moved.
 *
 * <p>Docker lists the container as {@code wallet-core-<purpose>-db-<4 hex>}: "test" for an
 * integration test, "demo" or "pluggy" for {@link TestCoreApplication}. The suffix keeps the
 * name free when two builds run at once (worktrees, or several cached test contexts). Nothing
 * here is started by hand: the core creates it and it goes away when the core stops.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** What the container is for; {@link TestCoreApplication} sets it, tests leave the default. */
    public static final String PURPOSE_PROPERTY = "wallet.container-purpose";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer(Environment environment) {
        String name = containerName(environment.getProperty(PURPOSE_PROPERTY, "test"));
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18"))
                .withCreateContainerCmdModifier(command -> {
                    command.withName(name);
                    // Like --rm: if Docker Desktop closes mid-run (Ryuk dies with it), the daemon
                    // deletes the container on its next start instead of leaving it behind.
                    command.getHostConfig().withAutoRemove(true);
                });
    }

    static String containerName(String purpose) {
        return "wallet-core-" + purpose + "-db-" + UUID.randomUUID().toString().substring(0, 4);
    }
}
