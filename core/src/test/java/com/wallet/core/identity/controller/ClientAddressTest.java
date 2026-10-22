package com.wallet.core.identity.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressTest {

    @Test
    void takesAnIpAddressAsTheBffSentIt() {
        assertThat(ClientAddress.of("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(ClientAddress.of(" 2001:DB8::1 ")).isEqualTo("2001:db8::1");
    }

    @Test
    void anythingElseSharesTheUnknownScope() {
        assertThat(ClientAddress.of(null)).isEqualTo(ClientAddress.UNKNOWN);
        assertThat(ClientAddress.of("evil|account:x")).isEqualTo(ClientAddress.UNKNOWN);
        assertThat(ClientAddress.of("1".repeat(46))).isEqualTo(ClientAddress.UNKNOWN);
    }
}
