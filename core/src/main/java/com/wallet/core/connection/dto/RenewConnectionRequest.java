package com.wallet.core.connection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** @param consentVersion the text of the purpose screen the user agreed to when renewing; required */
public record RenewConnectionRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9._-]+") String consentVersion) {
}
