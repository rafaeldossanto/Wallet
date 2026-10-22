package com.wallet.core.shared.error;

import lombok.Getter;

import java.time.Duration;

/**
 * An expensive kind of request has no free slot ({@code Bulkhead}). Answered at once with
 * {@code Retry-After}: the slots free up in milliseconds, and waiting here would only hold the
 * request until the BFF's read timeout counted it as the core being down.
 *
 * <p>The user's own share taken is 429 {@code rate_limit.exceeded}, the code the app already
 * shows for too many requests in a row; everyone's slots taken is 503 {@code service.busy}.
 */
@Getter
public class ServiceBusyException extends DomainException {

    public static final String BUSY = "service.busy";
    public static final String USER_SHARE_TAKEN = "rate_limit.exceeded";

    private static final Duration RETRY_AFTER = Duration.ofSeconds(1);

    private final Duration retryAfter;

    private ServiceBusyException(ErrorType type, String code, String message) {
        super(type, code, message);
        this.retryAfter = RETRY_AFTER;
    }

    public static ServiceBusyException userShareTaken(String kind) {
        return new ServiceBusyException(ErrorType.TOO_MANY_REQUESTS, USER_SHARE_TAKEN,
                "Too many " + kind + " requests of this user running at once");
    }

    public static ServiceBusyException full(String kind) {
        return new ServiceBusyException(ErrorType.UNAVAILABLE, BUSY, "Every " + kind + " slot is taken");
    }

    public static ServiceBusyException noDatabaseConnection() {
        return new ServiceBusyException(ErrorType.UNAVAILABLE, BUSY, "No database connection came free in time");
    }
}
