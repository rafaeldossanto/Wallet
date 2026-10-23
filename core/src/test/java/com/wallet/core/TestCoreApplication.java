package com.wallet.core;

import com.wallet.core.demo.DemoPluggy;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.springframework.util.StringUtils.hasText;

/**
 * Runs the core from the test classpath against the local Postgres, the same database as
 * {@code spring-boot:run} (README, "Banco local"): {@code ./mvnw spring-boot:test-run}. It never
 * creates a container, so nothing is left running in Docker; only the integration tests do
 * ({@link TestcontainersConfiguration}).
 *
 * <p>Without {@code PLUGGY_CLIENT_ID} it also starts {@link DemoPluggy}, so the app can link
 * the items in {@link DemoPluggy#ITEMS} (demo-nubank, demo-itau, demo-xp...) and show synthetic data.
 * With the variable set, it talks to the real Pluggy.
 */
@Slf4j
public class TestCoreApplication {

    public static void main(String[] args) throws IOException {
        if (!hasText(System.getenv("WALLET_DB_PASSWORD"))) {
            throw new IllegalStateException("WALLET_DB_PASSWORD is not set: test-run uses the local Postgres "
                    + "(wallet_dev). Create it once with core/db/setup-local.sql and set the variable "
                    + "(README, \"Banco local\").");
        }
        String[] arguments = args;
        if (!hasText(System.getenv("PLUGGY_CLIENT_ID"))) {
            DemoPluggy pluggy = DemoPluggy.start(Clock.systemUTC());
            Runtime.getRuntime().addShutdownHook(new Thread(pluggy::close));
            log.warn("PLUGGY_CLIENT_ID not set: demo Pluggy with synthetic data at {}. Link items {} in the app.",
                    pluggy.baseUrl(), String.join(", ", DemoPluggy.ITEMS));
            arguments = Stream.concat(Arrays.stream(args), Stream.of(
                    "--wallet.pluggy.base-url=" + pluggy.baseUrl(),
                    "--wallet.pluggy.client-id=demo",
                    "--wallet.pluggy.client-secret=demo")).toArray(String[]::new);
        }
        CoreApplication.main(arguments);
    }
}
