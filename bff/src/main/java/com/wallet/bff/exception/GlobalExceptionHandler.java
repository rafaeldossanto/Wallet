package com.wallet.bff.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Optional;

import static java.util.Objects.nonNull;

/** The only place in the BFF that returns {@code ResponseEntity}. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Relayed byte for byte: the core already wrote {@code { code, message }}. */
    @ExceptionHandler(CoreErrorException.class)
    public ResponseEntity<byte[]> handleCoreError(CoreErrorException ex) {
        if (ex.getBody().length == 0) {
            return ResponseEntity.status(ex.getStatus()).contentType(MediaType.APPLICATION_JSON)
                    .body(("{\"code\":\"core.error\",\"message\":\"Core answered " + ex.getStatus() + "\"}").getBytes());
        }
        return ResponseEntity.status(ex.getStatus()).contentType(MediaType.APPLICATION_JSON).body(ex.getBody());
    }

    @ExceptionHandler(CoreUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleCoreUnavailable(CoreUnavailableException ex) {
        log.warn("[EXCEPTION] bff.core_unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("bff.core_unavailable", "The service is temporarily unavailable"));
    }

    /** Same code and header as the core's own bulkheads: the app treats a busy BFF and a busy core alike. */
    @ExceptionHandler(CoreBusyException.class)
    public ResponseEntity<ErrorResponse> handleCoreBusy(CoreBusyException ex) {
        log.warn("[EXCEPTION] service.busy: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(new ErrorResponse("service.busy", "Too many requests of this kind right now; try again shortly"));
    }

    @ExceptionHandler(BffException.class)
    public ResponseEntity<ErrorResponse> handleBff(BffException ex) {
        log.warn("[EXCEPTION] {}: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadParameter(Exception ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("request.invalid_parameter", "A request parameter is missing or invalid"));
    }

    /** The JSON reader wraps the size limit's exception: a body cut at the limit is too large, not malformed. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        Optional<RequestBodyTooLargeException> tooLarge = causeOf(ex, RequestBodyTooLargeException.class);
        if (tooLarge.isPresent()) {
            return handleTooLarge(tooLarge.get());
        }
        return ResponseEntity.badRequest().body(new ErrorResponse("request.malformed", "Request body could not be read"));
    }

    /** A chunked body went past {@code wallet.http.max-body-size} while being read. */
    @ExceptionHandler(RequestBodyTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(RequestBodyTooLargeException ex) {
        log.warn("[EXCEPTION] request.too_large: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ErrorResponse("request.too_large", ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("route.not_found", "No route for " + ex.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("[EXCEPTION] Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("internal.error", "Unexpected error"));
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
