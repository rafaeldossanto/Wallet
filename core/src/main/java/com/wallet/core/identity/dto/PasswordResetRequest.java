package com.wallet.core.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The token of the "Esqueci minha senha" link, and the password to set. Same length rules as at sign-up. */
public record PasswordResetRequest(
        @NotBlank @Size(max = 128) String token,
        @NotBlank @Size(min = 8, max = 128) String newPassword) {
}
