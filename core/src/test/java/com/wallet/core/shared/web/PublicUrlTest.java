package com.wallet.core.shared.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Where the e-mailed links point. */
class PublicUrlTest {

    @Test
    void aDevelopmentMachineLinksToTheLocalBff() {
        assertThat(PublicUrl.resolve(null, false)).isEqualTo("http://localhost:8080");
        assertThat(PublicUrl.resolve("http://192.168.0.10:8080/", false)).isEqualTo("http://192.168.0.10:8080");
    }

    /** A link to localhost would leave every new account unconfirmed. */
    @Test
    void productionNeedsThePublicAddressOverHttps() {
        assertThatThrownBy(() -> PublicUrl.resolve(" ", true)).hasMessageContaining("WALLET_PUBLIC_URL");
        assertThatThrownBy(() -> PublicUrl.resolve("http://wallet.example.com", true)).hasMessageContaining("https");
        assertThatThrownBy(() -> PublicUrl.resolve("wallet.example.com", true)).hasMessageContaining("absolute");
        assertThat(PublicUrl.resolve("https://wallet.example.com//", true)).isEqualTo("https://wallet.example.com");
    }
}
