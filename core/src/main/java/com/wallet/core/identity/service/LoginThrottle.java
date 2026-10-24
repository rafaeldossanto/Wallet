package com.wallet.core.identity.service;

import com.wallet.core.identity.config.AuthProperties;
import com.wallet.core.identity.repository.LoginThrottles;
import com.wallet.core.shared.security.OpaqueTokens;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * The progressive lockout of sign-ins (finding A5), counted in two scopes:
 *
 * <ul>
 *   <li><b>source</b>: one e-mail from one client address. 5 failures lock that address out of
 *       that e-mail. This is what stops someone guessing from one place, and it does not touch the
 *       owner signing in from anywhere else: with only the victim's e-mail, a stranger can no
 *       longer keep the account locked, the weapon the old "5 errors, 15 minutes" handed out.</li>
 *   <li><b>account</b>: one e-mail from every address. 20 failures lock it for everyone. Only a
 *       guess spread over several addresses gets here, and then the owner is told by e-mail.</li>
 * </ul>
 *
 * <p>Each lockout of a scope lasts twice the one before ({@link Lockouts}), from 15 minutes up to a
 * day. A lockout is checked before the password is: a locked scope costs no BCrypt (finding A4).
 *
 * <p>Keyed by the e-mail, not by the account, and the same for e-mails without one: a lockout
 * answers alike whether the account exists or not, so it does not reveal which e-mails have
 * accounts (A16). The keys are SHA-256 hashes; the table holds no e-mail or address in clear.
 *
 * <p>Separate bean on purpose: a failure has to be committed even though the caller then throws.
 * Callers stay outside a transaction of their own, so this never needs a second connection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginThrottle {

    private final LoginThrottles throttles;
    private final AuthProperties properties;
    private final Clock clock;

    public Optional<Instant> lockedUntil(String email, String clientAddress) {
        return throttles.lockedUntil(accountKey(email), sourceKey(email, clientAddress), clock.instant());
    }

    /** @return the lockout, when this failure is the one that started it */
    @Transactional
    public Optional<Lockout> registerFailure(String email, String clientAddress) {
        Instant now = clock.instant();
        AuthProperties.Lockout policy = properties.lockout();
        // Always the account row first, then the source: the same order everywhere, no deadlock.
        Optional<Instant> account = fail(accountKey(email), policy.accountMaxFailures(), policy, now);
        Optional<Instant> source = fail(sourceKey(email, clientAddress), policy.sourceMaxFailures(), policy, now);
        if (account.isPresent()) {
            log.warn("[SECURITY] Sign-in locked for every address until {}", account.get());
            return account.map(until -> new Lockout(Lockout.Scope.ACCOUNT, until));
        }
        source.ifPresent(until -> log.warn("[SECURITY] Sign-in locked for one address until {}", until));
        return source.map(until -> new Lockout(Lockout.Scope.SOURCE, until));
    }

    /** The address got in: its own count starts over. The account count waits for a quiet day. */
    @Transactional
    public void registerSuccess(String email, String clientAddress) {
        throttles.delete(sourceKey(email, clientAddress));
    }

    /** A confirmed sign-up: whatever was tried before the confirmation no longer counts. */
    @Transactional
    public void forgetAccount(String email) {
        throttles.delete(accountKey(email));
    }

    private Optional<Instant> fail(String key, int maxFailures, AuthProperties.Lockout policy, Instant now) {
        Lockouts.Outcome outcome = Lockouts.afterFailure(throttles.lockForUpdate(key, now), maxFailures, policy, now);
        throttles.save(key, outcome.state());
        return outcome.locked() ? Optional.of(outcome.state().lockedUntil()) : Optional.empty();
    }

    private static String accountKey(String email) {
        return OpaqueTokens.hash("account:" + email);
    }

    private static String sourceKey(String email, String clientAddress) {
        return OpaqueTokens.hash("source:" + email + "|" + clientAddress);
    }

    /** @param scope who it locks out: one address, or everyone */
    public record Lockout(Scope scope, Instant until) {

        public enum Scope {
            SOURCE,
            ACCOUNT
        }
    }
}
