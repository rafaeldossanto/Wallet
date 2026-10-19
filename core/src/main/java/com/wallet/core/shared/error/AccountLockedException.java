package com.wallet.core.shared.error;

import lombok.Getter;

import java.time.Instant;

/**
 * Too many wrong passwords: the account takes none until {@link #getLockedUntil()}. The moment
 * goes to the client as a field ({@code lockedUntil}), so the app can say until when without
 * reading the English message, which is only for logs.
 */
@Getter
public class AccountLockedException extends DomainException {

    public static final String CODE = "auth.locked";

    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super(ErrorType.LOCKED, CODE, "Too many failed logins; try again after " + lockedUntil);
        this.lockedUntil = lockedUntil;
    }
}
