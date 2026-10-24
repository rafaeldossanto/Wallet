package com.wallet.core.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param refreshTokensAfter a refresh token that expired, or was revoked, this long ago is deleted:
 *                           it opens nothing, and the e-mailed links have their own clean-up
 */
@ConfigurationProperties("wallet.retention.identity")
public record IdentityRetentionProperties(@DefaultValue("30d") Duration refreshTokensAfter) {
}
