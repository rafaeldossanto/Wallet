package com.wallet.core.connection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param providerItemId the Pluggy item id: pasted from the Meu Pluggy dashboard today,
 *                       returned by the Pluggy Connect widget in production
 * @param consentVersion the text of the purpose screen the user agreed to ({@code open-finance-v1});
 *                       required, and recorded with the connection
 */
public record LinkConnectionRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9-]+") String providerItemId,
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9._-]+") String consentVersion) {
}
