package com.wallet.core.identity.service;

import com.wallet.core.identity.config.AuthProperties;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Every BCrypt of the core goes through here, a few at a time (finding A4). BCrypt is the point
 * of a password hash, slow on purpose, so a flood of sign-ins (or of sign-ups, which hash too)
 * could take every processor away from the rest of the Wallet. With at most {@code concurrency}
 * running at once the flood waits in line instead; whoever waits longer than {@code maxWait} gets
 * 429 {@code auth.busy} and tries again in a moment, while syncing and reading carry on.
 *
 * <p>This is the global limit. The per-e-mail one comes before it: a locked e-mail
 * ({@link LoginThrottle}) is refused without any hash, and the BFF limits each address.
 */
@Slf4j
@Component
public class PasswordHasher {

    private final PasswordEncoder encoder;
    private final Semaphore permits;
    private final Duration maxWait;

    /**
     * Compared against when the e-mail has no account, so it takes as long as a wrong password and
     * the response time does not reveal which accounts exist.
     */
    private final String timingEqualizerHash;

    public PasswordHasher(PasswordEncoder encoder, AuthProperties properties) {
        this.encoder = encoder;
        this.permits = new Semaphore(properties.passwordChecks().resolvedConcurrency(), true);
        this.maxWait = properties.passwordChecks().maxWait();
        this.timingEqualizerHash = encoder.encode("timing-equalizer");
    }

    public boolean matches(String rawPassword, String hash) {
        return gated(() -> encoder.matches(rawPassword, hash));
    }

    public String encode(String rawPassword) {
        return gated(() -> encoder.encode(rawPassword));
    }

    /** As long as a real check, for an e-mail without an account. */
    public void matchNothing(String rawPassword) {
        gated(() -> encoder.matches(rawPassword, timingEqualizerHash));
    }

    private <T> T gated(Supplier<T> hashing) {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(maxWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw busy();
        }
        if (!acquired) {
            log.warn("[SECURITY] Password check refused: {} already running or waiting", permits.getQueueLength());
            throw busy();
        }
        try {
            return hashing.get();
        } finally {
            permits.release();
        }
    }

    private static DomainException busy() {
        return new DomainException(ErrorType.TOO_MANY_REQUESTS, "auth.busy",
                "Too many password checks right now; try again in a moment");
    }
}
