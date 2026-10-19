package com.wallet.bff.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static org.springframework.util.StringUtils.hasText;

/**
 * The shared secret the core asks of every {@code /internal} call, sent in {@code X-Gateway-Key}.
 * Without it, whoever reaches the core's port gets nothing, not even the login route.
 *
 * <p>Empty in development: both services fall back to the same well-known key, so the local flow
 * keeps working without configuration. With the {@code prod} profile an empty key, a short one or
 * the development key stops the BFF from starting. The core applies the same rules.
 */
@Slf4j
@Component
public class GatewayKey {

    public static final String HEADER = "X-Gateway-Key";

    /** Development only. The core's {@code GatewayKey} has the same constant. */
    static final String DEVELOPMENT_KEY = "wallet-development-gateway-key-not-for-production";

    static final int MIN_LENGTH = 32;

    static final String PRODUCTION_PROFILE = "prod";

    private final String value;

    public GatewayKey(CoreProperties core, Environment environment) {
        this.value = resolve(core.gatewayKey(), environment.matchesProfiles(PRODUCTION_PROFILE));
    }

    public String value() {
        return value;
    }

    static String resolve(String configured, boolean production) {
        if (!hasText(configured)) {
            if (production) {
                throw new IllegalStateException("WALLET_GATEWAY_KEY is required with the prod profile: "
                        + "set the same value in the BFF and in the core (openssl rand -base64 48)");
            }
            log.warn("No WALLET_GATEWAY_KEY configured: using the development gateway key. "
                    + "Never run like this outside a development machine.");
            return DEVELOPMENT_KEY;
        }
        String key = configured.strip();
        if (key.length() < MIN_LENGTH) {
            throw new IllegalStateException("WALLET_GATEWAY_KEY must have at least " + MIN_LENGTH + " characters");
        }
        if (production && DEVELOPMENT_KEY.equals(key)) {
            throw new IllegalStateException("WALLET_GATEWAY_KEY is the development key; generate a real one for prod");
        }
        return key;
    }
}
