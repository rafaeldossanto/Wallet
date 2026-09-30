package com.wallet.core.identity.config;

import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Reads RSA keys in PEM, either inline ({@code -----BEGIN ...}) or from a file path.
 * Inline is handy in containers; a path is easier in a Windows environment variable.
 */
@UtilityClass
public class PemKeys {

    private static final String PEM_PREFIX = "-----BEGIN";

    public RSAPrivateKey readPrivateKey(String source) {
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decode(source)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("wallet.jwt.private-key is not a PKCS#8 RSA private key", ex);
        }
    }

    public RSAPublicKey readPublicKey(String source) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decode(source)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("wallet.jwt.public-key is not an X.509 RSA public key", ex);
        }
    }

    private byte[] decode(String source) {
        String pem = source.strip().startsWith(PEM_PREFIX) ? source : readFile(source.strip());
        String body = pem.lines()
                .filter(line -> !line.startsWith("-----"))
                .reduce("", String::concat);
        return Base64.getMimeDecoder().decode(body);
    }

    private String readFile(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read key file " + path, ex);
        }
    }
}
