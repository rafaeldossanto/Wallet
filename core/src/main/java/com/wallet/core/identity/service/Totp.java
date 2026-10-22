package com.wallet.core.identity.service;

import lombok.experimental.UtilityClass;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * TOTP (RFC 6238) with the parameters every authenticator app reads by default: HMAC-SHA1,
 * 6 digits, 30-second steps. The code of step {@code n} is HOTP (RFC 4226) with counter {@code n}.
 */
@UtilityClass
public class Totp {

    public static final int DIGITS = 6;
    public static final long STEP_SECONDS = 30;

    /** One step either side: a phone clock a little off, or a code typed as it changed. */
    public static final int WINDOW = 1;

    /** 160 bits, the size RFC 4226 recommends for the HMAC-SHA1 key. */
    private static final int SECRET_BYTES = 20;

    private static final SecureRandom RANDOM = new SecureRandom();

    public byte[] newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return secret;
    }

    public long step(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), STEP_SECONDS);
    }

    public String code(byte[] secret, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            int code = binary % (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", code);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA1 is not available in this JVM", ex);
        }
    }

    /**
     * The step whose code this is, within {@link #WINDOW} of now. The caller still has to check the
     * step was not used before: a code inside its window would otherwise work twice.
     */
    public OptionalLong matchingStep(byte[] secret, String code, Instant now) {
        byte[] presented = code.getBytes(StandardCharsets.US_ASCII);
        long current = step(now);
        OptionalLong match = OptionalLong.empty();
        // Every candidate is compared, in constant time, whether or not one matched already.
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            byte[] expected = code(secret, step, DIGITS).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, presented) && match.isEmpty()) {
                match = OptionalLong.of(step);
            }
        }
        return match;
    }

    /**
     * The {@code otpauth://} URI of the QR code (Google's Key Uri Format, which every app reads):
     * {@code otpauth://totp/Wallet:rafael@example.com?secret=...&issuer=Wallet}.
     */
    public String uri(String issuer, String account, String base32Secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /** Percent-encoding with {@code %20} for a space: the URI is not a form. */
    private String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
