package com.wallet.bff;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behind the deploy, Caddy hands to the BFF only the paths its {@code @bff} matcher lists; anything
 * else opens the Flutter app. A public page of the BFF missing from that list works in development,
 * where the BFF is reached on 8080 directly, and is never served in production: the password reset
 * page was, until this test. Every folder of {@code static} is a page an e-mailed link opens, so
 * each one, plus the API and the liveness probe, has to be in the matcher.
 */
class CaddyRoutesTest {

    private static final Path CADDYFILE = Path.of("..", "deploy", "caddy", "Caddyfile");
    private static final Path STATIC_PAGES = Path.of("src", "main", "resources", "static");

    @Test
    void caddyHandsEveryPublicPathOfTheBffToIt() throws IOException {
        List<String> handed = bffMatcher();

        assertThat(handed).contains("/api/*", "/livez");
        try (Stream<Path> folders = Files.list(STATIC_PAGES)) {
            List<String> pages = folders.filter(Files::isDirectory)
                    .map(folder -> "/" + folder.getFileName() + "/*")
                    .toList();
            assertThat(pages).isNotEmpty();
            assertThat(handed).containsAll(pages);
        }
    }

    private static List<String> bffMatcher() throws IOException {
        String line = Files.readAllLines(CADDYFILE).stream()
                .map(String::strip)
                .filter(candidate -> candidate.startsWith("@bff path "))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no @bff path matcher in " + CADDYFILE));
        return Arrays.asList(line.substring("@bff path ".length()).strip().split("\\s+"));
    }
}
