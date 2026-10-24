package com.wallet.core.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param version the version of the texts the user is looking at; refused when it is not the current one */
public record TermsAcceptanceRequest(@NotBlank @Size(max = 32) String version) {
}
