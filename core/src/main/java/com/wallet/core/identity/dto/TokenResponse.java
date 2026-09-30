package com.wallet.core.identity.dto;

import java.time.Instant;

/**
 * Always carries both tokens in the body: the core does not know about cookies. The BFF uses
 * {@code refreshTokenExpiresAt} to set the cookie lifetime on the web.
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        Instant refreshTokenExpiresAt) {
}
