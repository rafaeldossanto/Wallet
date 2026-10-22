package com.wallet.core.identity.dto;

/** What the opened link did, so the confirmation page says it in words. */
public record EmailConfirmationResponse(Result result) {

    public enum Result {
        ACCOUNT_CONFIRMED,
        EMAIL_CHANGED
    }
}
