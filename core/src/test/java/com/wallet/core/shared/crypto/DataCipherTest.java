package com.wallet.core.shared.crypto;

import com.wallet.core.shared.security.ProductionProfile;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataCipherTest {

    private final DataCipher cipher = new DataCipher(new CryptoProperties(randomKey()), new MockEnvironment());

    @Test
    void roundTripsAndNeverRepeatsTheCiphertext() {
        String first = cipher.encrypt("PADARIA DO BAIRRO");
        String second = cipher.encrypt("PADARIA DO BAIRRO");

        assertThat(first).startsWith("v1:").isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("PADARIA DO BAIRRO");
    }

    @Test
    void valueWrittenWithAnotherKeyComesBackAsNull() {
        String foreign = new DataCipher(new CryptoProperties(randomKey()), new MockEnvironment()).encrypt("secret");

        assertThat(cipher.decrypt(foreign)).isNull();
        assertThat(cipher.decrypt("not encrypted")).isNull();
    }

    @Test
    void keepsNullsAsNulls() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(cipher.fingerprint(null)).isNull();
    }

    @Test
    void aFingerprintIsStableForTheSameTextAndKeyAndRevealsNeither() {
        String fingerprint = cipher.fingerprint("condominio edificio sol");

        assertThat(fingerprint).matches("[0-9a-f]{32}").isEqualTo(cipher.fingerprint("condominio edificio sol"));
        assertThat(cipher.fingerprint("condominio edificio lua")).isNotEqualTo(fingerprint);
        assertThat(new DataCipher(new CryptoProperties(randomKey()), new MockEnvironment()).fingerprint("condominio edificio sol"))
                .isNotEqualTo(fingerprint);
    }

    @Test
    void rejectsAKeyOfTheWrongSize() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new DataCipher(new CryptoProperties(shortKey), new MockEnvironment())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withoutAKeyADevelopmentMachineGetsAnEphemeralOne() {
        DataCipher ephemeral = new DataCipher(new CryptoProperties(""), new MockEnvironment());

        assertThat(ephemeral.decrypt(ephemeral.encrypt("secret"))).isEqualTo("secret");
    }

    /** A server that made up its key would lose every description it encrypted on the next restart. */
    @Test
    void withTheProdProfileAMissingKeyStopsTheBoot() {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles(ProductionProfile.NAME);

        assertThatThrownBy(() -> new DataCipher(new CryptoProperties(" "), production))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WALLET_DATA_KEY");
        assertThat(new DataCipher(new CryptoProperties(randomKey()), production).encrypt("secret")).startsWith("v1:");
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
