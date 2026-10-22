package com.wallet.core.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param code the authenticator code, or a recovery code, when the account has the second factor
 *             on; without it such an account answers {@code auth.two_factor_required}
 */
public record LoginRequest(@NotBlank String email, @NotBlank String password, @Size(max = 32) String code) {
}
