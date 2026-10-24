package com.wallet.bff.model.dto.request;

/** Relayed as is: the purpose screen the user agreed to when renewing. */
public record RenewConnectionRequest(String consentVersion) {
}
