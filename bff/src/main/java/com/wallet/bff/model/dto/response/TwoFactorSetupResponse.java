package com.wallet.bff.model.dto.response;

/** @param otpauthUri what the app draws as a QR code; {@code secret} is for typing by hand */
public record TwoFactorSetupResponse(String secret, String otpauthUri) {
}
