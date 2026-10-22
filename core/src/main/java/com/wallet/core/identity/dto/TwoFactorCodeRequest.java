package com.wallet.core.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The first code from the authenticator app, which turns the second factor on. */
public record TwoFactorCodeRequest(@NotBlank @Size(max = 32) String code) {
}
