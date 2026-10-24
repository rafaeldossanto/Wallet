package com.wallet.bff.model.dto.request;

/**
 * Relayed as is: the core requires {@code consentVersion} (the purpose screen the user agreed to)
 * and answers {@code validation.failed} without it.
 */
public record LinkConnectionRequest(String providerItemId, String consentVersion) {
}
