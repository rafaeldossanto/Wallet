package com.wallet.bff.ratelimit;

import com.wallet.bff.config.GatewayKey;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * An address as the logs may hold it (LGPD: an IP is personal data). A keyed hash of the address,
 * cut to 12 hex characters: the same address gives the same text, so an abuse still shows as one
 * source across the log lines, but nobody can read the address back without the key, and the key
 * never leaves the BFF. It comes from the gateway key through a purpose of its own, so the
 * gateway key itself is never used as a hash key.
 *
 * <p>Only the logs get the pseudonym. The rate limit counts the real address in memory.
 */
@Component
public class IpPseudonyms {

    private static final String HMAC = "HmacSHA256";
    private static final String PURPOSE = "wallet-ip-log-v1";
    private static final int HEX_CHARACTERS = 12;

    private final SecretKeySpec key;

    public IpPseudonyms(GatewayKey gatewayKey) {
        this.key = new SecretKeySpec(hmac(new SecretKeySpec(gatewayKey.value().getBytes(StandardCharsets.UTF_8), HMAC),
                PURPOSE), HMAC);
    }

    public String of(String address) {
        return HexFormat.of().formatHex(hmac(key, address)).substring(0, HEX_CHARACTERS);
    }

    private static byte[] hmac(SecretKeySpec key, String text) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available", ex);
        }
    }
}
