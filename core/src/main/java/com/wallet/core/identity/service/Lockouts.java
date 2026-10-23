package com.wallet.core.identity.service;

import com.wallet.core.identity.config.AuthProperties;
import lombok.experimental.UtilityClass;

import java.time.Duration;
import java.time.Instant;

/**
 * The progressive lockout, as a pure rule: what one more failed sign-in does to a scope's counters.
 *
 * <p>{@code maxFailures} failures in a row lock the scope for {@code firstDuration}; while failures
 * keep coming, each lockout lasts twice the one before, up to {@code maxDuration}. A day without
 * failures ({@code forgetAfter}) and the scope starts over. Guessing gets exponentially slower,
 * while someone who mistyped a few times is back in minutes.
 */
@UtilityClass
public class Lockouts {

    /** @param locked whether this failure is the one that locked: the answer says so at once */
    public record Outcome(ThrottleState state, boolean locked) {
    }

    public Outcome afterFailure(ThrottleState current, int maxFailures, AuthProperties.Lockout policy, Instant now) {
        boolean forgotten = !current.lockedAt(now) && !current.lastFailureAt().plus(policy.forgetAfter()).isAfter(now);
        int failures = (forgotten ? 0 : current.failures()) + 1;
        int lockouts = forgotten ? 0 : current.lockouts();
        if (failures < maxFailures) {
            return new Outcome(new ThrottleState(failures, lockouts, forgotten ? null : current.lockedUntil(), now), false);
        }
        Instant until = now.plus(duration(lockouts, policy.firstDuration(), policy.maxDuration()));
        return new Outcome(new ThrottleState(0, lockouts + 1, until, now), true);
    }

    /** {@code first × 2^earlierLockouts}, never more than {@code max}. */
    public Duration duration(int earlierLockouts, Duration first, Duration max) {
        Duration duration = first;
        for (int lockout = 0; lockout < earlierLockouts && duration.compareTo(max) < 0; lockout++) {
            duration = duration.multipliedBy(2);
        }
        return duration.compareTo(max) < 0 ? duration : max;
    }
}
