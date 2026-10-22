package com.wallet.core.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param privateKey PEM text or path to a PEM file (PKCS#8). Only the core has it: it signs tokens.
 * @param publicKey  PEM text or path to a PEM file (X.509). The BFF gets this one to validate tokens.
 * @param audience   the {@code aud} of every access token, required by the core and the BFF. It says
 *                   the token is a user's session on the Wallet API (both hops: the BFF forwards the
 *                   same token): anything else the same key might ever sign, such as a short-lived
 *                   link or a service token, carries another audience and is refused as a session.
 */
@ConfigurationProperties("wallet.jwt")
public record JwtProperties(
        @DefaultValue("wallet-core") String issuer,
        @DefaultValue("wallet-api") String audience,
        String privateKey,
        String publicKey,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("30d") Duration refreshTokenTtl) {
}
