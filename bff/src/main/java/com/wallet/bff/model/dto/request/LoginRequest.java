package com.wallet.bff.model.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

/** @param code the authenticator code or a recovery code, once the core answered auth.two_factor_required */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginRequest(String email, String password, String code) {
}
