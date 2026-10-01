package com.wallet.bff.auth;

import com.wallet.bff.exception.BffException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

import static java.util.Objects.isNull;

/**
 * Which app is calling, from {@code X-Wallet-Client}. It decides where the refresh token goes, and
 * requiring it on {@code /api/auth} is the CSRF protection of the cookie: a form on another site
 * can make the browser send the cookie, but not set this header.
 */
public enum ClientType {
    WEB,
    MOBILE;

    public static ClientType from(String header) {
        if (isNull(header)) {
            throw required();
        }
        return switch (header.strip().toLowerCase(Locale.ROOT)) {
            case "web" -> WEB;
            case "mobile" -> MOBILE;
            default -> throw required();
        };
    }

    public boolean isWeb() {
        return WEB.equals(this);
    }

    private static BffException required() {
        return new BffException(HttpStatus.BAD_REQUEST, "auth.client_required",
                "Send X-Wallet-Client: web or mobile");
    }
}
