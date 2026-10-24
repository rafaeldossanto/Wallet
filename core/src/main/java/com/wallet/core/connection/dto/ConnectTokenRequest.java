package com.wallet.core.connection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param consentVersion the text of the purpose screen the user agreed to ({@code open-finance-v1});
 *                       required, so no widget opens for someone who was not shown it
 * @param connectionId   renewing: the connection whose authorization the widget updates (the token is
 *                       made for its item). Empty = a new connection
 */
public record ConnectTokenRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9._-]+") String consentVersion,
        UUID connectionId) {
}
