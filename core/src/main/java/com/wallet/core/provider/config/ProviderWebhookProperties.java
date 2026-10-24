package com.wallet.core.provider.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Registering the Wallet's webhooks at the provider: a command run on the server, not a setting of
 * the running core.
 *
 * @param register true only for that command ({@code --wallet.webhooks.register=true}): the core
 *                 registers the hooks and exits. Off in the core that serves the API
 * @param secret   the value the provider sends in {@code X-Wallet-Webhook-Secret} on every post
 *                 ({@code WALLET_PLUGGY_WEBHOOK_SECRET}, the same as in the BFF, 32+ characters)
 * @param baseUrl  the BFF's public address ({@code WALLET_PUBLIC_URL}): the hooks post to
 *                 {@code <baseUrl>/api/webhooks/pluggy}
 */
@ConfigurationProperties("wallet.webhooks")
public record ProviderWebhookProperties(@DefaultValue("false") boolean register, String secret, String baseUrl) {
}
