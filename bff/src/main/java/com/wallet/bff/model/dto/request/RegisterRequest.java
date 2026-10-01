package com.wallet.bff.model.dto.request;

/** Relayed as is: the core validates and answers validation.failed with the fields. */
public record RegisterRequest(String email, String password, String displayName) {
}
