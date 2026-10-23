package com.wallet.core.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The token of an e-mailed link, as the confirmation page read it from the link. */
public record EmailTokenRequest(@NotBlank @Size(max = 128) String token) {
}
