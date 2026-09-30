package com.wallet.core.shared.error;

/**
 * What went wrong, independent of HTTP. {@link GlobalExceptionHandler} maps each type to a status.
 */
public enum ErrorType {
    VALIDATION,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    UNPROCESSABLE,
    LOCKED,
    TOO_MANY_REQUESTS,
    UNAVAILABLE
}
