package com.wallet.core.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Body of every error response: {@code { "code": "...", "message": "...", "fields": [...] }}.
 * {@code fields} only appears on validation errors.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(String code, String message, List<FieldViolation> fields) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, List.of());
    }

    public record FieldViolation(String field, String message) {
    }
}
