package com.wallet.bff.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.springframework.util.StringUtils.hasText;

/**
 * The secret Pluggy's webhooks carry in {@code X-Wallet-Webhook-Secret}. Checked at startup like
 * the gateway key: with {@code prod} and webhooks on, a missing or short one stops the BFF naming
 * the variable. Compared in constant time (SHA-256 of both sides, then {@link MessageDigest#isEqual}),
 * as the core compares the gateway key, so the answer does not say how much of a guess was right.
 */
@Slf4j
@Component
public class WebhookSecret {

    public static final String HEADER = "X-Wallet-Webhook-Secret";
    static final int MIN_LENGTH = 32;

    /** Null = nobody gets in (webhooks off, or no secret outside production). */
    private final byte[] digest;

    @Autowired
    public WebhookSecret(WebhookProperties properties, Environment environment) {
        this(resolve(properties.secret(), properties.enabled(), environment.matchesProfiles(GatewayKey.PRODUCTION_PROFILE)));
        if (properties.enabled() && isNull(digest)) {
            log.warn("wallet.webhooks.enabled is on without WALLET_PLUGGY_WEBHOOK_SECRET: every webhook is refused (401). "
                    + "Development only; the prod profile does not start like this.");
        }
    }

    /** @param secret the resolved secret, or null for none */
    WebhookSecret(String secret) {
        this.digest = isNull(secret) ? null : sha256(secret);
    }

    /** True only for the exact secret. */
    public boolean matches(String presented) {
        return nonNull(digest) && nonNull(presented) && MessageDigest.isEqual(digest, sha256(presented));
    }

    /** @return the secret to use, or null for none */
    static String resolve(String configured, boolean enabled, boolean production) {
        if (!enabled) {
            return null;
        }
        if (!hasText(configured)) {
            if (production) {
                throw new IllegalStateException("WALLET_PLUGGY_WEBHOOK_SECRET is required with the prod profile when "
                        + "wallet.webhooks.enabled is on: the webhooks are not signed, the secret is all that tells Pluggy's "
                        + "calls apart (openssl rand -base64 48; the same value in the core that registers the hooks)");
            }
            return null;
        }
        String secret = configured.strip();
        if (secret.length() < MIN_LENGTH) {
            throw new IllegalStateException("WALLET_PLUGGY_WEBHOOK_SECRET must have at least " + MIN_LENGTH + " characters");
        }
        return secret;
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", ex);
        }
    }
}
