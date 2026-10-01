package com.wallet.core.identity.config;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** The key id is what makes the BFF fetch a new public key: it must follow the key. */
class JwtKeyConfigTest {

    private final JwtKeyConfig config = new JwtKeyConfig();

    @Test
    void theSameKeyKeepsItsIdAcrossRestarts() throws Exception {
        KeyPair keyPair = keyPair();

        RSAKey first = JwtKeyConfig.signingKey((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate());
        RSAKey second = JwtKeyConfig.signingKey((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate());

        assertThat(first.getKeyID()).isNotBlank().isEqualTo(second.getKeyID());
    }

    @Test
    void everyEphemeralKeyHasItsOwnId() {
        JwtProperties noKeys = new JwtProperties("wallet-core", null, null, Duration.ofMinutes(15), Duration.ofDays(30));

        RSAKey beforeRestart = config.jwtSigningKey(noKeys);
        RSAKey afterRestart = config.jwtSigningKey(noKeys);

        assertThat(beforeRestart.getKeyID()).isNotEqualTo(afterRestart.getKeyID());
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
