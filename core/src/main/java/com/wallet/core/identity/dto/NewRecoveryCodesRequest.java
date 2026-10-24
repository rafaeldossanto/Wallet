package com.wallet.core.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param code an authenticator code or a recovery code */
public record NewRecoveryCodesRequest(@NotBlank @Size(max = 128) String password, @NotBlank @Size(max = 32) String code) {
}
