package com.wallet.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** The BFF has no key: it validates with the core's public key, read from the core's JWKS. */
@ConfigurationProperties("wallet.jwt")
public record JwtProperties(@DefaultValue("wallet-core") String issuer) {
}
