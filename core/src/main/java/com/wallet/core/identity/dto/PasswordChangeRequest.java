package com.wallet.core.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The current password again, and the new one (same length rules as at sign-up). */
public record PasswordChangeRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(min = 8, max = 128) String newPassword) {
}
