package com.wallet.core.provider;

import java.util.List;
import java.util.Map;

/**
 * Port to the provider's webhook registry: which events it posts to the Wallet, and with which
 * header. Used only by the registration command; the Wallet treats what arrives as a hint and
 * never trusts it (see the {@code sync} module for the receiving side).
 */
public interface ProviderWebhooks {

    List<RegisteredWebhook> list();

    /** @param headers sent by the provider on every post, so the BFF can tell the call is meant for it */
    void create(String event, String url, Map<String, String> headers);

    /** Points an existing hook at {@code url} with {@code headers}, and enables it. */
    void update(String id, String url, Map<String, String> headers);
}
