package com.wallet.core.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagementSecurityConfigTest {

    /** On the core's own port the health checks would be public and read the database per request. */
    @Test
    void theManagementPortCannotBeTheCorePort() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.port", "8081")
                .withProperty("management.server.port", "8081");

        assertThatThrownBy(() -> new ManagementSecurityConfig().managementSecurityFilterChain(null, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WALLET_CORE_MANAGEMENT_PORT");
    }

    @Test
    void norLeftOutWhichMeansTheSamePort() {
        MockEnvironment environment = new MockEnvironment().withProperty("server.port", "8081");

        assertThatThrownBy(() -> new ManagementSecurityConfig().managementSecurityFilterChain(null, environment))
                .isInstanceOf(IllegalStateException.class);
    }
}
