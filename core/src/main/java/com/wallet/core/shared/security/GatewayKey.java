package com.wallet.core.shared.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static java.util.Objects.isNull;
import static org.springframework.util.StringUtils.hasText;

/**
 * The shared secret between the BFF and the core. The core does not assume a network topology:
 * whoever reaches its port without this key gets nothing, not even the login route or the JWKS.
 *
 * <p>Empty in development: both services fall back to the same well-known key, so the local flow
 * keeps working without configuration. With the {@code prod} profile an empty key, a short one or
 * the development key stops the core from starting. The BFF's {@code GatewayKey} applies the same rules.
 *
 * <p>The development key is in this public repository, so the core only takes it while it listens
 * on loopback ({@code server.address}): reachable from the network, it needs a real key.
 */
@Slf4j
@Component
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayKey {

    public static final String HEADER = "X-Gateway-Key";

    /** Development only. The BFF's {@code GatewayKey} has the same constant. Public for the tests. */
    public static final String DEVELOPMENT_KEY = "wallet-development-gateway-key-not-for-production";

    static final int MIN_LENGTH = 32;

    static final String PRODUCTION_PROFILE = "prod";

    /** Only the digest is kept: comparing digests takes the same time whatever the length presented. */
    private final byte[] digest;

    public GatewayKey(GatewayProperties properties, Environment environment) {
        String key = resolve(properties.key(), environment.matchesProfiles(PRODUCTION_PROFILE));
        if (DEVELOPMENT_KEY.equals(key) && !isLoopback(environment.getProperty("server.address"))) {
            throw new IllegalStateException("The development gateway key is public: with the core reachable from "
                    + "the network (server.address " + environment.getProperty("server.address", "unset") + ") set "
                    + "WALLET_GATEWAY_KEY, the same in the core and in the BFF, or WALLET_CORE_ADDRESS=127.0.0.1");
        }
        this.digest = sha256(key);
    }

    /** Constant time: how much of the key a caller got right does not show in the response time. */
    public boolean matches(String presented) {
        if (isNull(presented)) {
            return false;
        }
        return MessageDigest.isEqual(digest, sha256(presented));
    }

    static String resolve(String configured, boolean production) {
        if (!hasText(configured)) {
            if (production) {
                throw new IllegalStateException("WALLET_GATEWAY_KEY is required with the prod profile: "
                        + "set the same value in the core and in the BFF (openssl rand -base64 48)");
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

    /** Unset means every interface. Resolved once, at startup: {@code localhost} is a name. */
    static boolean isLoopback(String address) {
        if (!hasText(address)) {
            return false;
        }
        try {
            return InetAddress.getByName(address.strip()).isLoopbackAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", ex);
        }
    }
}
