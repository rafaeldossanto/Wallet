package com.wallet.bff.exception;

/**
 * Every slot of a group of the core's routes is in use ({@code CoreCallGuards}). The core is up:
 * the app is told to try again in a moment, and the other groups keep working.
 */
public class CoreBusyException extends RuntimeException {

    public CoreBusyException(String message, Throwable cause) {
        super(message, cause);
    }
}
