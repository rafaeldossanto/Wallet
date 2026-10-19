package com.wallet.core.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param key the shared secret the BFF sends in {@code X-Gateway-Key} on every call. Empty = the
 *            development key ({@link GatewayKey}); refused with the {@code prod} profile.
 */
@ConfigurationProperties("wallet.gateway")
public record GatewayProperties(String key) {
}
