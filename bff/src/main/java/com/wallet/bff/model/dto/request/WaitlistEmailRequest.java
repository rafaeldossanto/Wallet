package com.wallet.bff.model.dto.request;

/** What the core gets of a waitlist sign-up: the consent was checked here, and its version is the core's. */
public record WaitlistEmailRequest(String email) {
}
