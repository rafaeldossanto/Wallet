package com.wallet.core.identity.service;

import java.time.Instant;

import static java.util.Objects.nonNull;

/**
 * Failed sign-ins of one scope (an e-mail, or an e-mail from one client address).
 *
 * @param failures      since the last lockout, or since the counters were forgotten
 * @param lockouts      how many lockouts in a row: the next one lasts twice the previous
 * @param lockedUntil   {@code null} or in the past = not locked
 * @param lastFailureAt when the latest failure came; a quiet day and everything starts over
 */
public record ThrottleState(int failures, int lockouts, Instant lockedUntil, Instant lastFailureAt) {

    public static ThrottleState fresh(Instant now) {
        return new ThrottleState(0, 0, null, now);
    }

    public boolean lockedAt(Instant now) {
        return nonNull(lockedUntil) && lockedUntil.isAfter(now);
    }
}
