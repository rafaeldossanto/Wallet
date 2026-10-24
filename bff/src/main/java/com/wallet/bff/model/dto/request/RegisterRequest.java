package com.wallet.bff.model.dto.request;

/**
 * Relayed as is: the core validates and answers validation.failed with the fields.
 *
 * @param acceptTerms the box of the Terms of Use and the Privacy Policy; the core refuses without it (L15)
 */
public record RegisterRequest(String email, String password, String displayName, Boolean acceptTerms) {
}
