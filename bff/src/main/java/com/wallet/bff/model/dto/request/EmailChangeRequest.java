package com.wallet.bff.model.dto.request;

/** @param password the account's password again; the core checks it */
public record EmailChangeRequest(String newEmail, String password) {
}
