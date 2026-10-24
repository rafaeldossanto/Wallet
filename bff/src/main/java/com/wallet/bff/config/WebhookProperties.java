package com.wallet.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

import static java.util.Objects.isNull;

/**
 * Pluggy's webhooks ({@code POST /api/webhooks/pluggy}). They are not signed, so the route is a
 * door for hints: closed by default, opened with a secret, and optionally with a list of addresses.
 *
 * @param enabled    off (the default): the route does not exist (404). On with {@code prod}, the secret is required
 * @param secret     what the provider sends in {@code X-Wallet-Webhook-Secret} ({@code WALLET_PLUGGY_WEBHOOK_SECRET},
 *                   32+ characters, the same one the core registers the hooks with)
 * @param allowedIps IPs or CIDR ranges the provider calls from ({@code WALLET_PLUGGY_WEBHOOK_IPS}); empty = not checked.
 *                   The address is the one the rate limit sees ({@code wallet.rate-limit.trusted-proxies} included)
 */
@ConfigurationProperties("wallet.webhooks")
public record WebhookProperties(
        @DefaultValue("false") boolean enabled,
        String secret,
        @DefaultValue List<String> allowedIps) {

    public WebhookProperties {
        allowedIps = isNull(allowedIps) ? List.of() : List.copyOf(allowedIps);
    }
}
