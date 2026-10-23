package com.wallet.bff.model.dto.request;

/** The token the confirmation page read from the e-mailed link. */
public record EmailTokenRequest(String token) {
}
