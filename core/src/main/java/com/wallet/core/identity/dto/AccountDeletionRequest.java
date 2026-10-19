package com.wallet.core.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The account's password again: deleting it cannot be undone, so the access token alone does not do. */
public record AccountDeletionRequest(@NotBlank @Size(max = 128) String password) {
}
