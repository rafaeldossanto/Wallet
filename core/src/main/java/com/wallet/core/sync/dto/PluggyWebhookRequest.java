package com.wallet.core.sync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The three fields of Pluggy's webhook the Wallet reads. The webhook is not signed, so it is a hint:
 * nothing else in the body is looked at, kept or logged, and the Wallet reads Pluggy for itself.
 */
public record PluggyWebhookRequest(
        @NotBlank @Size(max = 100) String eventId,
        @Size(max = 64) String event,
        @Size(max = 100) String itemId) {
}
