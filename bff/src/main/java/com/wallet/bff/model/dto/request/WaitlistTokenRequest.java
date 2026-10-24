package com.wallet.bff.model.dto.request;

/** The token of an e-mailed waitlist link. Relayed as is: the core validates it. */
public record WaitlistTokenRequest(String token) {
}
