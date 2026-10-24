package com.wallet.core.provider.service;

import com.wallet.core.provider.ProviderCallContext;
import com.wallet.core.provider.ProviderCallReason;
import com.wallet.core.provider.ProviderWebhooks;
import com.wallet.core.provider.RegisteredWebhook;
import com.wallet.core.provider.config.ProviderWebhookProperties;
import com.wallet.core.shared.security.ProductionProfile;
import com.wallet.core.shared.web.PublicUrl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static java.util.Objects.isNull;

/**
 * Makes sure the provider posts the events the Wallet wants to
 * {@code <WALLET_PUBLIC_URL>/api/webhooks/pluggy}, with the {@code X-Wallet-Webhook-Secret} header
 * (global registration through the provider's API: a connect token carries no header). Idempotent:
 * it lists what exists, updates (and enables) the hooks that already post to the Wallet's address,
 * and creates the events that have none. A hook that posts somewhere else is left alone.
 *
 * <p>The events are hints that an item changed, and nothing else: item updated, in error or deleted,
 * and transactions created, updated or deleted. The Wallet reads the provider for itself on each.
 */
@Slf4j
@Service
public class WebhookRegistration {

    /** The header the provider adds to every post; the BFF checks it. */
    public static final String SECRET_HEADER = "X-Wallet-Webhook-Secret";
    public static final String PATH = "/api/webhooks/pluggy";
    static final List<String> EVENTS = List.of("item/updated", "item/error", "item/deleted",
            "transactions/created", "transactions/updated", "transactions/deleted");
    static final int MIN_SECRET_LENGTH = 32;

    private final ProviderWebhooks webhooks;
    private final ProviderWebhookProperties properties;
    private final Environment environment;

    public WebhookRegistration(ProviderWebhooks webhooks, ProviderWebhookProperties properties, Environment environment) {
        this.webhooks = webhooks;
        this.properties = properties;
        this.environment = environment;
    }

    /** What the run did. */
    public record Report(int created, int updated) {
    }

    /** @throws IllegalStateException when the secret or the public address is missing or unsafe, before any call */
    public Report register() {
        String secret = secret();
        String url = PublicUrl.resolve(properties.baseUrl(), ProductionProfile.isActive(environment)) + PATH;
        Map<String, String> headers = Map.of(SECRET_HEADER, secret);
        return new ProviderCallContext(null, null, null, ProviderCallReason.WEBHOOK_SETUP).call(() -> {
            List<RegisteredWebhook> existing = webhooks.list();
            int created = 0;
            int updated = 0;
            for (String event : EVENTS) {
                List<RegisteredWebhook> ours = existing.stream()
                        .filter(hook -> event.equals(hook.event()) && url.equals(hook.url()))
                        .toList();
                if (ours.isEmpty()) {
                    webhooks.create(event, url, headers);
                    created++;
                    continue;
                }
                for (RegisteredWebhook hook : ours) {
                    webhooks.update(hook.id(), url, headers);
                    updated++;
                }
            }
            log.info("[WEBHOOK] Registered at the provider: {} created, {} updated", created, updated);
            return new Report(created, updated);
        });
    }

    private String secret() {
        String secret = properties.secret();
        if (isNull(secret) || secret.strip().length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("WALLET_PLUGGY_WEBHOOK_SECRET is required to register the webhooks, with at least "
                    + MIN_SECRET_LENGTH + " characters (openssl rand -base64 48); the BFF needs the same value");
        }
        return secret.strip();
    }
}
