package com.wallet.core.waitlist.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The consent is the BFF's to check; the core records the version of the text it was given for. */
public record WaitlistRequest(@NotBlank @Email @Size(max = 320) String email) {
}
