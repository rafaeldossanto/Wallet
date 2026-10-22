package com.wallet.core.identity.service;

import com.wallet.core.identity.config.AuthProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LockoutsTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final AuthProperties.Lockout POLICY = new AuthProperties.Lockout(5, 20,
            Duration.ofMinutes(15), Duration.ofHours(24), Duration.ofHours(24));

    @Test
    void countsUntilTheLimitAndTheLastOneLocks() {
        ThrottleState state = ThrottleState.fresh(NOW);
        for (int failure = 1; failure < 5; failure++) {
            Lockouts.Outcome outcome = Lockouts.afterFailure(state, 5, POLICY, NOW);
            assertThat(outcome.locked()).isFalse();
            state = outcome.state();
        }
        assertThat(state.failures()).isEqualTo(4);

        Lockouts.Outcome fifth = Lockouts.afterFailure(state, 5, POLICY, NOW);

        assertThat(fifth.locked()).isTrue();
        assertThat(fifth.state().lockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(fifth.state().failures()).isZero();
        assertThat(fifth.state().lockouts()).isOne();
        assertThat(fifth.state().lockedAt(NOW.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(fifth.state().lockedAt(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void eachLockoutLastsTwiceTheOneBefore() {
        ThrottleState afterTwoLockouts = new ThrottleState(4, 2, NOW.minusSeconds(60), NOW.minusSeconds(60));

        Lockouts.Outcome third = Lockouts.afterFailure(afterTwoLockouts, 5, POLICY, NOW);

        assertThat(third.state().lockedUntil()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(third.state().lockouts()).isEqualTo(3);
    }

    @Test
    void noLockoutLastsMoreThanADay() {
        assertThat(Lockouts.duration(0, Duration.ofMinutes(15), Duration.ofHours(24))).isEqualTo(Duration.ofMinutes(15));
        assertThat(Lockouts.duration(6, Duration.ofMinutes(15), Duration.ofHours(24))).isEqualTo(Duration.ofHours(16));
        assertThat(Lockouts.duration(7, Duration.ofMinutes(15), Duration.ofHours(24))).isEqualTo(Duration.ofHours(24));
        assertThat(Lockouts.duration(1_000, Duration.ofMinutes(15), Duration.ofHours(24))).isEqualTo(Duration.ofHours(24));
    }

    /** Someone who mistyped yesterday does not start today one failure from a long lockout. */
    @Test
    void aQuietDayStartsEverythingOver() {
        ThrottleState yesterday = new ThrottleState(4, 3, NOW.minus(Duration.ofHours(30)), NOW.minus(Duration.ofHours(25)));

        Lockouts.Outcome outcome = Lockouts.afterFailure(yesterday, 5, POLICY, NOW);

        assertThat(outcome.locked()).isFalse();
        assertThat(outcome.state()).isEqualTo(new ThrottleState(1, 0, null, NOW));
    }

    @Test
    void withinTheDayTheCountGoesOn() {
        ThrottleState earlier = new ThrottleState(2, 1, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(2)));

        Lockouts.Outcome outcome = Lockouts.afterFailure(earlier, 5, POLICY, NOW);

        assertThat(outcome.state().failures()).isEqualTo(3);
        assertThat(outcome.state().lockouts()).isOne();
        assertThat(outcome.state().lastFailureAt()).isEqualTo(NOW);
    }
}
