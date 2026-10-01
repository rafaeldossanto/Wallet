package com.wallet.bff.exception;

/** Same shape as the core's errors, so the app handles both alike: it only reads {@code code}. */
public record ErrorResponse(String code, String message) {
}
