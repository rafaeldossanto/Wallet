package com.wallet.bff.client;

import java.time.Instant;

/** The core's login/refresh answer. Never sent to the app as is: see SessionDelivery. */
public record CoreTokens(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        Instant refreshTokenExpiresAt) {
}
