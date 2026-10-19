package com.wallet.bff.service;

import com.wallet.bff.auth.ClientType;
import com.wallet.bff.cache.ScreenCache;
import com.wallet.bff.client.CoreApi;
import com.wallet.bff.exception.BffException;
import com.wallet.bff.exception.CoreErrorException;
import com.wallet.bff.model.dto.request.LoginRequest;
import com.wallet.bff.model.dto.request.RefreshRequest;
import com.wallet.bff.model.dto.request.RegisterRequest;
import com.wallet.bff.model.dto.response.SessionResponse;
import com.wallet.bff.model.dto.response.UserResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static java.util.Objects.isNull;
import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final CoreApi coreApi;
    private final SessionDelivery delivery;
    private final ScreenCache cache;

    public UserResponse register(RegisterRequest request) {
        return coreApi.register(request);
    }

    /**
     * Deletes the account in the core, which also revokes the bank connections and every refresh
     * token. Then the session ends here too: the web cookie is dropped (harmless for the apps,
     * which forget their tokens themselves) and no cached screen of the user is left.
     * Nothing changes when the core refuses: the error goes to the app as it came.
     */
    public void deleteAccount(UUID userId, HttpServletResponse response) {
        coreApi.deleteMe();
        delivery.clearCookie(response);
        cache.evictUser(userId);
    }

    public SessionResponse login(ClientType client, LoginRequest request, HttpServletResponse response) {
        return delivery.deliver(client, coreApi.login(request), response);
    }

    /** A refused refresh on the web also drops the cookie, so the browser stops sending a dead token. */
    public SessionResponse refresh(ClientType client, RefreshRequest body, String cookie, HttpServletResponse response) {
        String refreshToken = refreshTokenOf(client, body, cookie);
        if (!hasText(refreshToken)) {
            throw new BffException(HttpStatus.UNAUTHORIZED, "auth.invalid_refresh_token", "No refresh token");
        }
        try {
            return delivery.deliver(client, coreApi.refresh(refreshToken), response);
        } catch (CoreErrorException ex) {
            if (client.isWeb() && ex.isUnauthorized()) {
                delivery.clearCookie(response);
            }
            throw ex;
        }
    }

    public void logout(ClientType client, RefreshRequest body, String cookie, HttpServletResponse response) {
        String refreshToken = refreshTokenOf(client, body, cookie);
        if (client.isWeb()) {
            delivery.clearCookie(response);
        }
        if (hasText(refreshToken)) {
            coreApi.logout(refreshToken);
        }
    }

    private static String refreshTokenOf(ClientType client, RefreshRequest body, String cookie) {
        if (client.isWeb()) {
            return cookie;
        }
        return isNull(body) ? null : body.refreshToken();
    }
}
