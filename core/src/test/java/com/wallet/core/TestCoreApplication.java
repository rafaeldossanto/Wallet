package com.wallet.core;

import com.wallet.core.demo.DemoPluggy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;

import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.springframework.util.StringUtils.hasText;

/**
 * Runs the core against a throwaway Postgres in Docker, without touching the local database:
 * {@code ./mvnw spring-boot:test-run}.
 *
 * <p>Without {@code PLUGGY_CLIENT_ID} it also starts {@link DemoPluggy}, so the app can link
 * the items in {@link DemoPluggy#ITEMS} (demo-nubank, demo-itau, demo-xp...) and show synthetic data.
 * With the variable set, it talks to the real Pluggy.
 */
@Slf4j
public class TestCoreApplication {

    public static void main(String[] args) throws IOException {
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
        SpringApplication.from(CoreApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(arguments);
    }
}
