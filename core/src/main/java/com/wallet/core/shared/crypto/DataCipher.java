package com.wallet.core.shared.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import static java.util.Objects.isNull;
import static org.springframework.util.StringUtils.hasText;

/**
 * AES-256-GCM for data that must not be readable from a database dump (transaction descriptions).
 *
 * <p>Stored as {@code v1:base64(iv || ciphertext+tag)}. The version prefix lets a future key
 * rotation tell old values from new ones.
 */
@Slf4j
@Component
@EnableConfigurationProperties(CryptoProperties.class)
public class DataCipher {

    private static final String VERSION = "v1:";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public DataCipher(CryptoProperties properties) {
        this.key = new SecretKeySpec(resolveKey(properties.dataKey()), "AES");
    }

    public String encrypt(String plaintext) {
        if (isNull(plaintext)) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
            return VERSION + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not encrypt", ex);
        }
    }

    /**
     * A value written with another key (for example, before a restart with the ephemeral key)
     * comes back as {@code null} instead of failing the whole read.
     */
    public String decrypt(String stored) {
        if (isNull(stored)) {
            return null;
        }
        if (!stored.startsWith(VERSION)) {
            log.warn("[CRYPTO] Value without a known version prefix; returning it as unreadable");
            return null;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(VERSION.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
            byte[] plaintext = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            log.warn("[CRYPTO] Could not decrypt a value (written with another key?)");
            return null;
        }
    }

    private static byte[] resolveKey(String configured) {
        if (!hasText(configured)) {
            log.warn("No WALLET_DATA_KEY configured: using an ephemeral key. Encrypted data written now "
                    + "becomes unreadable after a restart. Generate one with: openssl rand -base64 32");
            byte[] ephemeral = new byte[KEY_BYTES];
            new SecureRandom().nextBytes(ephemeral);
            return ephemeral;
        }
        byte[] decoded = Base64.getDecoder().decode(configured.strip());
        if (decoded.length != KEY_BYTES) {
            throw new IllegalStateException("wallet.crypto.data-key must be 32 bytes in base64 (AES-256)");
        }
        return decoded;
    }
}
