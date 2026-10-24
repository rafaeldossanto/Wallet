package com.wallet.bff.model.dto.request;

/** @param version the version of the texts the user read; the core refuses one that is not the current */
public record TermsAcceptanceRequest(String version) {
}
