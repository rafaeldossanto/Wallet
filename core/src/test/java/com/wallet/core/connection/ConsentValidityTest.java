package com.wallet.core.connection;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentValidityTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    void anAuthorizationEndsOnItsDate() {
        assertThat(ConsentValidity.ended(ConnectionStatus.ACTIVE, NOW.plusSeconds(1), NOW)).isFalse();
        assertThat(ConsentValidity.ended(ConnectionStatus.ACTIVE, NOW, NOW)).isTrue();
        assertThat(ConsentValidity.ended(ConnectionStatus.ACTIVE, NOW.minus(Duration.ofDays(1)), NOW)).isTrue();
    }

    @Test
    void withoutADateItNeverEndsUnlessMarked() {
        assertThat(ConsentValidity.ended(ConnectionStatus.ACTIVE, null, NOW)).isFalse();
        assertThat(ConsentValidity.ended(ConnectionStatus.NEEDS_ATTENTION, null, NOW)).isFalse();
        assertThat(ConsentValidity.ended(ConnectionStatus.CONSENT_EXPIRED, null, NOW)).isTrue();
        assertThat(ConsentValidity.ended(ConnectionStatus.CONSENT_EXPIRED, NOW.plus(Duration.ofDays(5)), NOW)).isTrue();
    }

    @Test
    void aRenewalNeedsALaterDateInTheFuture() {
        Instant old = NOW.minus(Duration.ofDays(1));

        assertThat(ConsentValidity.renewed(old, NOW.plus(Duration.ofDays(90)), NOW)).isTrue();
        assertThat(ConsentValidity.renewed(null, NOW.plus(Duration.ofDays(90)), NOW)).isTrue();
        // Closed the widget: Pluggy still says the old date, or none.
        assertThat(ConsentValidity.renewed(old, old, NOW)).isFalse();
        assertThat(ConsentValidity.renewed(old, null, NOW)).isFalse();
        // A date that is not even in the future is no renewal.
        assertThat(ConsentValidity.renewed(old.minus(Duration.ofDays(5)), NOW, NOW)).isFalse();
    }

    @Test
    void theScopesAreTheFourKindsOfDataTheScreenListsInTheSameOrder() {
        assertThat(ConsentScopes.current()).isEqualTo("ACCOUNTS,TRANSACTIONS,CREDIT_CARDS,INVESTMENTS");
    }
}
