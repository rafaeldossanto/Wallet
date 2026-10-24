package com.wallet.bff.model.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Relayed as is. {@code consentVersion} is required by the core; {@code connectionId} makes it the
 * token of a renewal (the widget in update mode, on that connection's item).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConnectTokenRequest(String consentVersion, UUID connectionId) {
}
