package com.wallet.bff.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Same rules as the core's GatewayKey: a BFF the core would refuse should not start either. */
class GatewayKeyTest {

    private static final String KEY = "a-real-gateway-key-with-more-than-32-characters";

    @Test
    void developmentFallsBackToTheDevelopmentKey() {
        assertThat(GatewayKey.resolve("", false)).isEqualTo(GatewayKey.DEVELOPMENT_KEY);
        assertThat(GatewayKey.resolve(null, false)).isEqualTo(GatewayKey.DEVELOPMENT_KEY);
    }

    @Test
    void productionWithoutAKeyDoesNotStart() {
        assertThatThrownBy(() -> GatewayKey.resolve(" ", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WALLET_GATEWAY_KEY is required");
    }

    @Test
    void productionRefusesTheDevelopmentKey() {
        assertThatThrownBy(() -> GatewayKey.resolve(GatewayKey.DEVELOPMENT_KEY, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("development key");
    }

    @Test
    void aShortKeyIsRefusedEverywhere() {
        assertThatThrownBy(() -> GatewayKey.resolve("short", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
    }

    @Test
    void aRealKeyIsUsedTrimmed() {
        assertThat(GatewayKey.resolve("  " + KEY + "\n", true)).isEqualTo(KEY);
    }
}
