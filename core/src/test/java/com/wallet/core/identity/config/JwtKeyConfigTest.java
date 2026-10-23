package com.wallet.core.identity.config;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The key id is what makes the BFF fetch a new public key: it must follow the key. */
class JwtKeyConfigTest {

    @Test
    void theSameKeyKeepsItsIdAcrossRestarts() throws Exception {
        KeyPair keyPair = keyPair();

        RSAKey first = JwtKeyConfig.signingKey((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate());
        RSAKey second = JwtKeyConfig.signingKey((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate());

        assertThat(first.getKeyID()).isNotBlank().isEqualTo(second.getKeyID());
    }

    @Test
    void everyEphemeralKeyHasItsOwnId() {
        JwtProperties noKeys = new JwtProperties("wallet-core", "wallet-api", null, null, Duration.ofMinutes(15), Duration.ofDays(30));

        RSAKey beforeRestart = JwtKeyConfig.resolve(noKeys, false);
        RSAKey afterRestart = JwtKeyConfig.resolve(noKeys, false);

        assertThat(beforeRestart.getKeyID()).isNotEqualTo(afterRestart.getKeyID());
    }

    /** A server that made up its key would log everyone out on every restart. */
    @Test
    void withTheProdProfileMissingKeysStopTheBoot() {
        JwtProperties noKeys = new JwtProperties("wallet-core", "wallet-api", "", "", Duration.ofMinutes(15), Duration.ofDays(30));

        assertThatThrownBy(() -> JwtKeyConfig.resolve(noKeys, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WALLET_JWT_PRIVATE_KEY");
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
