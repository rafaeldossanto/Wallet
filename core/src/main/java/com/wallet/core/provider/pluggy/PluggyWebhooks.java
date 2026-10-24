package com.wallet.core.provider.pluggy;

import com.wallet.core.provider.ProviderWebhooks;
import com.wallet.core.provider.RegisteredWebhook;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static java.util.Objects.nonNull;

/** Pluggy's webhook registry ({@code /webhooks}), apart from the data adapter so each can be replaced alone. */
@Component
class PluggyWebhooks implements ProviderWebhooks {

    private final PluggyClient client;

    PluggyWebhooks(PluggyClient client) {
        this.client = client;
    }

    @Override
    public List<RegisteredWebhook> list() {
        return client.getWebhooks().stream()
                .map(webhook -> new RegisteredWebhook(webhook.id(), webhook.event(), webhook.url(), nonNull(webhook.disabledAt())))
                .toList();
    }

    @Override
    public void create(String event, String url, Map<String, String> headers) {
        client.createWebhook(event, url, headers);
    }

    @Override
    public void update(String id, String url, Map<String, String> headers) {
        client.updateWebhook(id, url, headers);
    }
}
