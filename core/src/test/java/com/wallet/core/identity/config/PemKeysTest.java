package com.wallet.core.identity.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PemKeysTest {

    private static KeyPair keyPair;
    private static String privatePem;
    private static String publicPem;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        privatePem = pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
        publicPem = pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
    }

    @Test
    void readsInlinePem() {
        assertThat(PemKeys.readPrivateKey(privatePem).getEncoded()).isEqualTo(keyPair.getPrivate().getEncoded());
        assertThat(PemKeys.readPublicKey(publicPem).getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
    }

    @Test
    void readsPemFromAFilePath(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("public.pem"), publicPem);

        assertThat(PemKeys.readPublicKey(file.toString()).getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
    }

    @Test
    void refusesAPublicKeyWhereThePrivateOneIsExpected() {
        assertThatThrownBy(() -> PemKeys.readPrivateKey(publicPem)).isInstanceOf(IllegalStateException.class);
    }

    private static String pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }
}
