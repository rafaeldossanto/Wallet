package com.wallet.bff.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The webhook secret, checked at startup like the gateway key, and compared without leaking where it differs. */
class WebhookSecretTest {

    private static final String SECRET = "a-webhook-secret-of-at-least-32-characters";

    @Test
    void productionWithWebhooksOnNeedsASecretOfAtLeast32Characters() {
        assertThatThrownBy(() -> WebhookSecret.resolve(null, true, true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("WALLET_PLUGGY_WEBHOOK_SECRET");
        assertThatThrownBy(() -> WebhookSecret.resolve(" ", true, true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("WALLET_PLUGGY_WEBHOOK_SECRET");
        assertThatThrownBy(() -> WebhookSecret.resolve("too short", true, true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least 32");
        assertThat(WebhookSecret.resolve("  " + SECRET + "\n", true, true)).isEqualTo(SECRET);
    }

    @Test
    void withWebhooksOffNothingIsRequired() {
        assertThat(WebhookSecret.resolve(null, false, true)).isNull();
        assertThat(WebhookSecret.resolve("", false, true)).isNull();
    }

    /** Outside production it starts, and no call gets in: there is no value to match. */
    @Test
    void developmentWithoutASecretLetsNobodyIn() {
        assertThat(WebhookSecret.resolve(null, true, false)).isNull();
        assertThat(new WebhookSecret(null).matches("anything")).isFalse();
        assertThat(new WebhookSecret(null).matches(null)).isFalse();
    }

    @Test
    void aShortSecretIsRefusedEverywhere() {
        assertThatThrownBy(() -> WebhookSecret.resolve("short", true, false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least 32");
    }

    @Test
    void onlyTheExactSecretMatches() {
        WebhookSecret secret = new WebhookSecret(SECRET);

        assertThat(secret.matches(SECRET)).isTrue();
        assertThat(secret.matches(SECRET + "x")).isFalse();
        assertThat(secret.matches(SECRET.substring(1))).isFalse();
        assertThat(secret.matches("")).isFalse();
        assertThat(secret.matches(null)).isFalse();
    }
}
