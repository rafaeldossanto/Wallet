package com.wallet.core.waitlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The token of an e-mailed link, as the BFF's page read it from the link. */
public record WaitlistTokenRequest(@NotBlank @Size(max = 128) String token) {
}
