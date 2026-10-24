package com.wallet.bff.model.dto.request;

/** The token the reset page read from the e-mailed link, and the password it collected. */
public record PasswordResetRequest(String token, String newPassword) {
}
