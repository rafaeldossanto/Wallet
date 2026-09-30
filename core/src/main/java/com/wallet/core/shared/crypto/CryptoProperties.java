package com.wallet.core.shared.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param dataKey base64 of 32 random bytes (AES-256): {@code openssl rand -base64 32}.
 *                Empty = ephemeral key for local development only.
 */
@ConfigurationProperties("wallet.crypto")
public record CryptoProperties(String dataKey) {
}
