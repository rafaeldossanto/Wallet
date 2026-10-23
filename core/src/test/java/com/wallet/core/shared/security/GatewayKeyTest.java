package com.wallet.core.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayKeyTest {

    private static final String KEY = "a-real-gateway-key-with-more-than-32-characters";

    @Test
    void developmentFallsBackToTheDevelopmentKey() {
        GatewayKey gatewayKey = new GatewayKey(new GatewayProperties(""), listeningOn("127.0.0.1"));

        assertThat(gatewayKey.matches(GatewayKey.DEVELOPMENT_KEY)).isTrue();
        assertThat(new GatewayKey(new GatewayProperties(""), listeningOn("localhost")).matches(GatewayKey.DEVELOPMENT_KEY))
                .isTrue();
    }

    /** The development key is in the public repository: whoever reaches the port would know it. */
    @Test
    void theDevelopmentKeyIsRefusedWhenTheCoreIsReachableFromTheNetwork() {
        for (MockEnvironment environment : List.of(listeningOn("0.0.0.0"), listeningOn("192.168.0.10"), new MockEnvironment())) {
            assertThatThrownBy(() -> new GatewayKey(new GatewayProperties(""), environment))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("WALLET_CORE_ADDRESS=127.0.0.1");
            assertThatThrownBy(() -> new GatewayKey(new GatewayProperties(GatewayKey.DEVELOPMENT_KEY), environment))
                    .isInstanceOf(IllegalStateException.class);
        }

        assertThat(new GatewayKey(new GatewayProperties(KEY), listeningOn("0.0.0.0")).matches(KEY)).isTrue();
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
        assertThatThrownBy(() -> new GatewayKey(new GatewayProperties("short"), listeningOn("127.0.0.1")))
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

    private static MockEnvironment listeningOn(String address) {
        return new MockEnvironment().withProperty("server.address", address);
    }

    private static MockEnvironment production() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(ProductionProfile.NAME);
        return environment;
    }
}
