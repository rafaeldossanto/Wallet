package com.wallet.bff.exception;

/** The core did not answer (down, timed out) or the circuit breaker is open. */
public class CoreUnavailableException extends RuntimeException {

    public CoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
