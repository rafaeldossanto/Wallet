package com.wallet.core.identity.service;

import com.wallet.core.identity.config.AuthProperties;
import com.wallet.core.shared.error.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The global limit of finding A4: at most N password checks at once, the rest wait or get auth.busy. */
class PasswordHasherTest {

    @Test
    void aCheckThatWaitsTooLongForItsTurnGetsBusy() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PasswordHasher hasher = new PasswordHasher(new BlockingEncoder(running, release), properties(1, Duration.ofMillis(100)));
        Thread first = Thread.ofVirtual().start(() -> hasher.matches("password", "hash"));
        try {
            assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> hasher.matches("password", "hash"))
                    .isInstanceOf(DomainException.class)
                    .hasFieldOrPropertyWithValue("code", "auth.busy");
        } finally {
            release.countDown();
            first.join();
        }
        // Its turn came back: the next one runs.
        assertThat(hasher.matches("password", "hash")).isTrue();
    }

    @Test
    void zeroMeansHalfTheProcessors() {
        assertThat(properties(0, Duration.ofSeconds(2)).passwordChecks().resolvedConcurrency())
                .isEqualTo(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        assertThat(properties(3, Duration.ofSeconds(2)).passwordChecks().resolvedConcurrency()).isEqualTo(3);
    }

    private static AuthProperties properties(int concurrency, Duration maxWait) {
        return new AuthProperties(Duration.ofMinutes(1),
                new AuthProperties.Lockout(5, 20, Duration.ofMinutes(15), Duration.ofHours(24), Duration.ofHours(24)),
                new AuthProperties.PasswordChecks(concurrency, maxWait),
                new AuthProperties.EmailLinks(null, Duration.ofHours(24), 3),
                Duration.ofDays(7), Duration.ofHours(1), "0 15 4 * * *");
    }

    /** The first check holds its turn until released; the rest answer at once. */
    private static final class BlockingEncoder implements PasswordEncoder {

        private final CountDownLatch running;
        private final CountDownLatch release;
        private boolean first = true;

        private BlockingEncoder(CountDownLatch running, CountDownLatch release) {
            this.running = running;
            this.release = release;
        }

        @Override
        public String encode(CharSequence rawPassword) {
            return "hash";
        }

        @Override
        public synchronized boolean matches(CharSequence rawPassword, String encodedPassword) {
            if (first) {
                first = false;
                running.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            return true;
        }
    }
}
