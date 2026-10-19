package com.wallet.bff.model.dto.request;

/** @param password the account's password again; the core checks it before deleting anything */
public record AccountDeletionRequest(String password) {
}
