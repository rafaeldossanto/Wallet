package com.wallet.core.identity.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegalPropertiesTest {

    @Test
    void blankValuesFallBackToTheDefaultsSoNobodyIsAskedAgainByAMistake() {
        LegalProperties properties = new LegalProperties("", " ", null);

        assertThat(properties.termsVersion()).isEqualTo("2026-10-preliminar");
        assertThat(properties.termsUrl()).isEqualTo("http://localhost:5181/termos.html");
        assertThat(properties.privacyUrl()).isEqualTo("http://localhost:5181/privacidade.html");
    }

    @Test
    void whatIsConfiguredIsKeptWithoutTheEdges() {
        LegalProperties properties = new LegalProperties(" 2027-02 ", "https://wallet.example.com/termos.html",
                "https://wallet.example.com/privacidade.html");

        assertThat(properties.termsVersion()).isEqualTo("2027-02");
        assertThat(properties.termsUrl()).isEqualTo("https://wallet.example.com/termos.html");
    }
}
