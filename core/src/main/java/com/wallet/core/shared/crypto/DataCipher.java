package com.wallet.core.shared.crypto;

import com.wallet.core.shared.security.ProductionProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import static java.util.Objects.isNull;
import static org.springframework.util.StringUtils.hasText;

/**
 * AES-256-GCM for data that must not be readable from a database dump (transaction descriptions).
 *
 * <p>Stored as {@code v1:base64(iv || ciphertext+tag)}. The version prefix lets a future key
 * rotation tell old values from new ones.
 *
 * <p>{@link #fingerprint} is for the other need: finding a value again without storing it.
 *
 * <p>Without a key a development machine gets an ephemeral one; with the {@code prod} profile the
 * boot fails instead, since everything encrypted would be unreadable after a restart.
 */
@Slf4j
@Component
@EnableConfigurationProperties(CryptoProperties.class)
public class DataCipher {

    private static final String VERSION = "v1:";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String HMAC = "HmacSHA256";
    /** 128 bits: no two texts of one user will ever share a fingerprint. */
    private static final int FINGERPRINT_BYTES = 16;

    private final SecretKey key;
    private final SecretKey fingerprintKey;
    private final SecureRandom random = new SecureRandom();

    public DataCipher(CryptoProperties properties, Environment environment) {
        byte[] keyBytes = resolveKey(properties.dataKey(), ProductionProfile.isActive(environment));
        this.key = new SecretKeySpec(keyBytes, "AES");
        // Its own key, derived from the data key: the AES key is never used for anything else.
        this.fingerprintKey = new SecretKeySpec(hmac(new SecretKeySpec(keyBytes, HMAC), "wallet-fingerprint-v1"), HMAC);
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

    /**
     * A keyed hash (HMAC-SHA256, 32 hex characters): the same text always gives the same
     * fingerprint, so it can be stored and compared, and without the key a database dump does not
     * tell which text it was. Like {@link #encrypt}, it changes with the key.
     */
    public String fingerprint(String text) {
        if (isNull(text)) {
            return null;
        }
        byte[] digest = hmac(fingerprintKey, text);
        return HexFormat.of().formatHex(digest, 0, FINGERPRINT_BYTES);
    }

    private static byte[] hmac(SecretKey macKey, String text) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(macKey);
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is not available in this JVM", ex);
        }
    }

    private static byte[] resolveKey(String configured, boolean production) {
        if (!hasText(configured)) {
            if (production) {
                throw new IllegalStateException("WALLET_DATA_KEY is required with the prod profile: an ephemeral key "
                        + "would leave the encrypted data unreadable after a restart (openssl rand -base64 32)");
            }
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
