package com.wallet.core.identity.dto;

/**
 * @param secret     base32, for typing into the app by hand
 * @param otpauthUri what the QR code holds ({@code otpauth://totp/...})
 */
public record TwoFactorSetupResponse(String secret, String otpauthUri) {
}
