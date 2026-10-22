package com.wallet.bff.model.dto.request;

/** @param code an authenticator code or a recovery code */
public record TwoFactorDisableRequest(String password, String code) {
}
