package com.wallet.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The BFF has no key: it validates with the core's public key, read from the core's JWKS.
 *
 * @param audience the {@code aud} every access token must carry, the same the core issues and
 *                 checks: a user's session on the Wallet API. A token without it (issued before it
 *                 existed) or with another one is refused with 401, and the app renews it.
 */
@ConfigurationProperties("wallet.jwt")
public record JwtProperties(@DefaultValue("wallet-core") String issuer, @DefaultValue("wallet-api") String audience) {
}
