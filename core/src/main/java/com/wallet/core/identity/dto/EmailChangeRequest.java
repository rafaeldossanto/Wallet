package com.wallet.core.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The password again: the e-mail is how the account is found, and where its notices go. */
public record EmailChangeRequest(
        @NotBlank @Email @Size(max = 254) String newEmail,
        @NotBlank @Size(max = 128) String password) {
}
