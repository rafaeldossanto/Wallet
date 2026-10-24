package com.wallet.core.sync;

import java.util.UUID;

/**
 * A webhook was recorded for the first time. Published inside the transaction that recorded it, so
 * the handler runs after the commit and off the request thread. Carries what the handler needs and
 * no more: the row's id and the two fields it acts on.
 */
public record WebhookReceived(UUID id, String event, String itemId) {
}
