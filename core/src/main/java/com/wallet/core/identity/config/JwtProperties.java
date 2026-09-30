package com.wallet.core.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param privateKey PEM text or path to a PEM file (PKCS#8). Only the core has it: it signs tokens.
 * @param publicKey  PEM text or path to a PEM file (X.509). The BFF gets this one to validate tokens.
 */
@ConfigurationProperties("wallet.jwt")
public record JwtProperties(
        @DefaultValue("wallet-core") String issuer,
        String privateKey,
        String publicKey,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("30d") Duration refreshTokenTtl) {
}
