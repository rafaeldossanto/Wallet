package com.wallet.bff.model.dto.request;

/** The current password again, and the new one. */
public record PasswordChangeRequest(String currentPassword, String newPassword) {
}
