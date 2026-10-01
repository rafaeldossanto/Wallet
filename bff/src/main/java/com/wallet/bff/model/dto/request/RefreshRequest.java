package com.wallet.bff.model.dto.request;

/** Mobile only. On the web the refresh token comes from the cookie and never from the body. */
public record RefreshRequest(String refreshToken) {
}
