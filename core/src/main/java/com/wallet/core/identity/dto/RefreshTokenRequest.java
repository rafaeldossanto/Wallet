package com.wallet.core.identity.dto;

import jakarta.validation.constraints.NotBlank;

/** Used by refresh and logout. The BFF reads it from the cookie on the web and forwards it here. */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
