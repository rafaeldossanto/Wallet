package com.wallet.core.identity.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** The SHA-1 seed of RFC 6238, appendix B. */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest
    @CsvSource({
            "59, 94287082",
            "1111111109, 07081804",
            "1111111111, 14050471",
            "1234567890, 89005924",
            "2000000000, 69279037",
            "20000000000, 65353130"
    })
    void matchesTheTestVectorsOfTheRfc(long epochSecond, String expected) {
        long step = Totp.step(Instant.ofEpochSecond(epochSecond));

        assertThat(Totp.code(RFC_SECRET, step, 8)).isEqualTo(expected);
        // Six digits, as the apps show them, are the last six.
        assertThat(Totp.code(RFC_SECRET, step, Totp.DIGITS)).isEqualTo(expected.substring(2));
    }

    @Test
    void acceptsTheStepsNextToNowAndNothingFurther() {
        Instant now = Instant.ofEpochSecond(1_234_567_890);
        long step = Totp.step(now);

        assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, step, 6), now)).hasValue(step);
        assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, step - 1, 6), now)).hasValue(step - 1);
        assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, step + 1, 6), now)).hasValue(step + 1);
        assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, step - 2, 6), now)).isEmpty();
        assertThat(Totp.matchingStep(RFC_SECRET, "12345", now)).isEmpty();
    }

    @Test
    void secretsAre160RandomBits() {
        assertThat(Totp.newSecret()).hasSize(20).isNotEqualTo(Totp.newSecret());
    }

    @Test
    void theUriIsWhatAuthenticatorAppsRead() {
        assertThat(Totp.uri("Wallet", "rafael+teste@example.com", "JBSWY3DPEHPK3PXP")).isEqualTo(
                "otpauth://totp/Wallet:rafael%2Bteste%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=Wallet"
                        + "&algorithm=SHA1&digits=6&period=30");
    }
}
