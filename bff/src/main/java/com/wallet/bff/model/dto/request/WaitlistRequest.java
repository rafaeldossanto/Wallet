package com.wallet.bff.model.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What the landing page's form sends. The only request the BFF validates itself: it checks the
 * consent (the core records which version of the text it was given for). {@code consent} is a
 * {@code Boolean} so a missing one reaches the validation as {@code null}, and not the JSON reader.
 */
public record WaitlistRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull @AssertTrue Boolean consent) {
}
