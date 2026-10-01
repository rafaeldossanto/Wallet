package com.wallet.bff.exception;

import lombok.Getter;

/**
 * The core answered with an error. Status and body are kept as they came, so the app gets the
 * core's {@code code} untouched.
 */
@Getter
public class CoreErrorException extends RuntimeException {

    private final int status;
    private final byte[] body;

    public CoreErrorException(int status, byte[] body) {
        super("Core answered " + status);
        this.status = status;
        this.body = body;
    }

    public boolean isUnauthorized() {
        return status == 401;
    }
}
