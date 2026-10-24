package com.wallet.core.sync.service;

import lombok.experimental.UtilityClass;

/**
 * What came of a webhook, as recorded in {@code provider_webhook_events.outcome}. Pure: it only
 * names things.
 */
@UtilityClass
public class WebhookOutcomes {

    /** A sync was started (it runs in the background; its own result is in {@code sync_runs}). */
    public static final String SYNC_REQUESTED = "SYNC_REQUESTED";
    /** The manual cooldown is still running: the poll reads the connection later. */
    public static final String SKIPPED_COOLDOWN = "SKIPPED_COOLDOWN";
    /** A sync of the connection is already running, or the user's or the whole bulkhead is full. */
    public static final String SKIPPED_BUSY = "SKIPPED_BUSY";
    public static final String UNKNOWN_ITEM = "UNKNOWN_ITEM";
    public static final String ITEM_DELETED = "ITEM_DELETED";
    public static final String CONSENT_EXPIRED = "CONSENT_EXPIRED";
    public static final String FAILED = "FAILED";

    /** Why the rules of the manual update refused the sync a hint asked for, by their error code. */
    public String ofRefusal(String errorCode) {
        return switch (errorCode) {
            case "sync.too_soon" -> SKIPPED_COOLDOWN;
            case "sync.in_progress", "rate_limit.exceeded", "service.busy" -> SKIPPED_BUSY;
            case "connection.consent_expired" -> CONSENT_EXPIRED;
            default -> FAILED;
        };
    }
}
