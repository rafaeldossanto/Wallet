package com.wallet.bff.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** An error the BFF raises itself (not one relayed from the core). */
@Getter
public class BffException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BffException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
