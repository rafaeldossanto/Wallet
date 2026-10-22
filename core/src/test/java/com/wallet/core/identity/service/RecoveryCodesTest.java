package com.wallet.core.identity.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryCodesTest {

    @Test
    void tenDifferentCodesWithNothingToConfuse() {
        List<String> codes = RecoveryCodes.generate();

        assertThat(codes).hasSize(10).doesNotHaveDuplicates()
                .allMatch(code -> code.matches("[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){3}"));
    }

    @Test
    void howTheCodeIsTypedDoesNotMatter() {
        String hash = RecoveryCodes.hash("AB0C-1DEF-GH1J-KMNP");

        assertThat(RecoveryCodes.hash("ab0c 1def gh1j kmnp")).isEqualTo(hash);
        assertThat(RecoveryCodes.hash("ABOC-IDEF-GHLJ-KMNP")).isEqualTo(hash);
        assertThat(RecoveryCodes.hash("AB0C-1DEF-GH1J-KMNQ")).isNotEqualTo(hash);
    }

    @Test
    void sixDigitsAreTheAppsCodeAndAnythingElseARecoveryCode() {
        assertThat(RecoveryCodes.isAuthenticatorCode("123456")).isTrue();
        assertThat(RecoveryCodes.isAuthenticatorCode("123 456")).isTrue();
        assertThat(RecoveryCodes.isAuthenticatorCode("12345")).isFalse();
        assertThat(RecoveryCodes.isAuthenticatorCode("AB0C-1DEF-GH1J-KMNP")).isFalse();
    }
}
