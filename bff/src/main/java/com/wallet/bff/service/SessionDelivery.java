package com.wallet.bff.service;

import com.wallet.bff.auth.ClientType;
import com.wallet.bff.client.CoreTokens;
import com.wallet.bff.config.WebProperties;
import com.wallet.bff.model.dto.response.SessionResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/**
 * Hands the core's tokens to each kind of app. Mobile keeps the refresh token in the platform's
 * secure storage, so it gets it in the body. The browser gets it in a cookie the page's JavaScript
 * cannot read ({@code HttpOnly}), sent only to {@code /api/auth} and never cross-site.
 */
@Service
@RequiredArgsConstructor
public class SessionDelivery {

    public static final String REFRESH_COOKIE = "wallet_refresh";
    private static final String COOKIE_PATH = "/api/auth";

    private final WebProperties web;
    private final Clock clock;

    public SessionResponse deliver(ClientType client, CoreTokens tokens, HttpServletResponse response) {
        if (!client.isWeb()) {
            return new SessionResponse(tokens.accessToken(), tokens.tokenType(), tokens.expiresIn(),
                    tokens.refreshToken(), tokens.refreshTokenExpiresAt());
        }
        Duration maxAge = Duration.between(clock.instant(), tokens.refreshTokenExpiresAt());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), maxAge).toString());
        return new SessionResponse(tokens.accessToken(), tokens.tokenType(), tokens.expiresIn(), null, null);
    }

    public void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(web.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
