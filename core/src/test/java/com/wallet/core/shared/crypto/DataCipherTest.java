package com.wallet.core.shared.crypto;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataCipherTest {

    private final DataCipher cipher = new DataCipher(new CryptoProperties(randomKey()));

    @Test
    void roundTripsAndNeverRepeatsTheCiphertext() {
        String first = cipher.encrypt("PADARIA DO BAIRRO");
        String second = cipher.encrypt("PADARIA DO BAIRRO");

        assertThat(first).startsWith("v1:").isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("PADARIA DO BAIRRO");
    }

    @Test
    void valueWrittenWithAnotherKeyComesBackAsNull() {
        String foreign = new DataCipher(new CryptoProperties(randomKey())).encrypt("secret");

        assertThat(cipher.decrypt(foreign)).isNull();
        assertThat(cipher.decrypt("not encrypted")).isNull();
    }

    @Test
    void keepsNullsAsNulls() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void rejectsAKeyOfTheWrongSize() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new DataCipher(new CryptoProperties(shortKey))).isInstanceOf(IllegalStateException.class);
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
