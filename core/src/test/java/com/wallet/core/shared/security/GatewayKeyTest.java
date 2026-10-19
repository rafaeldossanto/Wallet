package com.wallet.core.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayKeyTest {

    private static final String KEY = "a-real-gateway-key-with-more-than-32-characters";

    @Test
    void developmentFallsBackToTheDevelopmentKey() {
        GatewayKey gatewayKey = new GatewayKey(new GatewayProperties(""), new MockEnvironment());

        assertThat(gatewayKey.matches(GatewayKey.DEVELOPMENT_KEY)).isTrue();
    }

    @Test
    void productionWithoutAKeyDoesNotStart() {
        assertThatThrownBy(() -> new GatewayKey(new GatewayProperties(null), production()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WALLET_GATEWAY_KEY is required");
    }

    @Test
    void productionRefusesTheDevelopmentKey() {
        assertThatThrownBy(() -> new GatewayKey(new GatewayProperties(GatewayKey.DEVELOPMENT_KEY), production()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("development key");
    }

    @Test
    void aShortKeyIsRefusedEverywhere() {
        assertThatThrownBy(() -> new GatewayKey(new GatewayProperties("short"), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
    }

    @Test
    void matchesOnlyTheConfiguredKey() {
        GatewayKey gatewayKey = new GatewayKey(new GatewayProperties("  " + KEY + "\n"), production());

        assertThat(gatewayKey.matches(KEY)).isTrue();
        assertThat(gatewayKey.matches(KEY + "x")).isFalse();
        assertThat(gatewayKey.matches(KEY.substring(1))).isFalse();
        assertThat(gatewayKey.matches(GatewayKey.DEVELOPMENT_KEY)).isFalse();
        assertThat(gatewayKey.matches("")).isFalse();
        assertThat(gatewayKey.matches(null)).isFalse();
    }

    private static MockEnvironment production() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(GatewayKey.PRODUCTION_PROFILE);
        return environment;
    }
}
