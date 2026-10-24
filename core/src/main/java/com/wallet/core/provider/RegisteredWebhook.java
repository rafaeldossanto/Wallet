package com.wallet.core.provider;

/**
 * A webhook as the provider lists it. The provider never shows the headers it was given, so a hook
 * is told apart by what it listens to and where it posts.
 */
public record RegisteredWebhook(String id, String event, String url, boolean disabled) {
}
