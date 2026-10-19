package com.wallet.core.shared.web;

import java.io.IOException;

/**
 * A body without {@code Content-Length} (chunked) went past the limit while being read. An
 * {@link IOException} because that is what reading a stream may throw; the handler answers 413.
 */
public class RequestBodyTooLargeException extends IOException {

    public RequestBodyTooLargeException(long limit) {
        super("Request body is larger than " + limit + " bytes");
    }
}
