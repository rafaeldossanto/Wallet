package com.wallet.core.shared.error;

import lombok.Getter;

/**
 * A business error the client can act on.
 *
 * <p>{@code code} is a stable identifier such as {@code sync.too_soon}: the app translates it
 * to Portuguese, so it must never change once published. {@code message} is for developers
 * reading logs and is not shown to the user.
 */
@Getter
public class DomainException extends RuntimeException {

    private final ErrorType type;
    private final String code;

    public DomainException(ErrorType type, String code, String message) {
        super(message);
        this.type = type;
        this.code = code;
    }
}
