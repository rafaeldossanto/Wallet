package com.wallet.core.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Body of every error response: {@code { "code": "...", "message": "...", "fields": [...] }}.
 * {@code fields} only appears on validation errors, {@code lockedUntil} only on {@code auth.locked}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(String code, String message, List<FieldViolation> fields, Instant lockedUntil) {

    public ErrorResponse(String code, String message, List<FieldViolation> fields) {
        this(code, message, fields, null);
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, List.of());
    }

    public record FieldViolation(String field, String message) {
    }
}
