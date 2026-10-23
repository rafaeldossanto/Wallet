package com.wallet.bff.model.dto.response;

/** @param result {@code ACCOUNT_CONFIRMED} or {@code EMAIL_CHANGED} */
public record EmailConfirmationResponse(String result) {
}
