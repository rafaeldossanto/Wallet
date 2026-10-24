package com.wallet.bff.model.dto.request;

/** Relayed as is: the core validates it and answers 202 for any e-mail. */
public record ForgotPasswordRequest(String email) {
}
