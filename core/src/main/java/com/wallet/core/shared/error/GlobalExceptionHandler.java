package com.wallet.core.shared.error;

import com.wallet.core.shared.web.RequestBodyTooLargeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.nonNull;

/**
 * Turns exceptions into {@link ErrorResponse}. The only place in the core that returns
 * {@code ResponseEntity}: controllers return their DTO directly.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TRACE_HEADER = "X-Trace-Id";

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException ex) {
        log.warn("[EXCEPTION] {}: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(statusOf(ex.getType()))
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage()));
    }

    /** Like any domain error, plus until when: the app tells the user the time, not "15 minutes". */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleLocked(AccountLockedException ex) {
        log.warn("[EXCEPTION] {}: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(statusOf(ex.getType()))
                .body(new ErrorResponse(ex.getCode(), ex.getMessage(), List.of(), ex.getLockedUntil()));
    }

    /** Like any domain error, plus when to try again: the slots free up in milliseconds. */
    @ExceptionHandler(ServiceBusyException.class)
    public ResponseEntity<ErrorResponse> handleBusy(ServiceBusyException ex) {
        log.warn("[EXCEPTION] {}: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(statusOf(ex.getType()))
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfter().toSeconds()))
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage()));
    }

    /**
     * No database connection came free within {@code spring.datasource.hikari.connection-timeout}
     * (the pool is full, or the database is down): a 503 to try again, sent before the BFF's read
     * timeout, which would count the wait as the core being down. Any other database error is
     * unexpected.
     */
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> handleDataAccess(RuntimeException ex) {
        if (causeOf(ex, SQLTransientConnectionException.class).isPresent()) {
            return handleBusy(ServiceBusyException.noDatabaseConnection());
        }
        return handleUnexpected(ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldViolation> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        log.warn("[EXCEPTION] validation.failed: {}", fields);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("validation.failed", "Request has invalid fields", fields));
    }

    /** The JSON reader wraps the size limit's exception: a body cut at the limit is too large, not malformed. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        Optional<RequestBodyTooLargeException> tooLarge = causeOf(ex, RequestBodyTooLargeException.class);
        if (tooLarge.isPresent()) {
            return handleTooLarge(tooLarge.get());
        }
        log.warn("[EXCEPTION] request.malformed: {}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("request.malformed", "Request body could not be read"));
    }

    /** A chunked body went past {@code wallet.http.max-body-size} while being read. */
    @ExceptionHandler(RequestBodyTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(RequestBodyTooLargeException ex) {
        log.warn("[EXCEPTION] request.too_large: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ErrorResponse.of("request.too_large", ex.getMessage()));
    }

    /** A query parameter that does not parse ({@code ?month=setembro}) or that is missing. */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadParameter(Exception ex) {
        log.warn("[EXCEPTION] request.invalid_parameter: {}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("request.invalid_parameter", "A request parameter is missing or invalid"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("route.not_found", "No route for " + ex.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // Class and stack only: the message of a database or parser error can hold the user's data.
        log.error("[EXCEPTION] Unexpected error, trace {}: {}", traceId(), ExceptionLog.describe(ex));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("internal.error", "Unexpected error"));
    }

    /** The BFF's {@code X-Trace-Id} of the request being answered, to find its other log lines. */
    private static String traceId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String header = attributes.getRequest().getHeader(TRACE_HEADER);
            if (nonNull(header) && header.matches("[A-Za-z0-9-]{1,32}")) {
                return header;
            }
        }
        return "-";
    }

    private static HttpStatus statusOf(ErrorType type) {
        return switch (type) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case LOCKED -> HttpStatus.LOCKED;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private static <T extends Throwable> Optional<T> causeOf(Throwable error, Class<T> type) {
        for (Throwable cause = error; nonNull(cause); cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return Optional.of(type.cast(cause));
            }
        }
        return Optional.empty();
    }
}
