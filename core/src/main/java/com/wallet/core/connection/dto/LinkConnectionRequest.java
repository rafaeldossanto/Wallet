package com.wallet.core.connection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param providerItemId the Pluggy item id: pasted from the Meu Pluggy dashboard today,
 *                       returned by the Pluggy Connect widget in production
 */
public record LinkConnectionRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9-]+") String providerItemId) {
}
