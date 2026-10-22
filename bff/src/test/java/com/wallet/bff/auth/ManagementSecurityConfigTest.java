package com.wallet.bff.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagementSecurityConfigTest {

    /** On the public port the health details and the readiness probe would be open to the internet. */
    @Test
    void theManagementPortCannotBeThePublicPort() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.port", "8080")
                .withProperty("management.server.port", "8080");

        assertThatThrownBy(() -> new ManagementSecurityConfig().managementSecurityFilterChain(null, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WALLET_BFF_MANAGEMENT_PORT");
    }
}
