package com.wallet.bff.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * What the app gets after login or refresh. On the web {@code refreshToken} and its expiry are
 * absent: the token travels only in the HttpOnly cookie, out of reach of the page's JavaScript.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        Instant refreshTokenExpiresAt) {
}
