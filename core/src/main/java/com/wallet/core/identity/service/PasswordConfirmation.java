package com.wallet.core.identity.service;

import com.wallet.core.identity.entity.User;
import com.wallet.core.shared.error.AccountLockedException;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * The password again, for what an access token alone must not do: delete the account, move it to
 * another e-mail, turn the second factor on or off. A stolen token, or a phone left unlocked, is
 * not enough for any of them.
 *
 * <p>A wrong password counts toward the lockout like a failed sign-in, in the same scopes. Call it
 * outside a transaction (see {@link LoginThrottle}).
 */
@Component
@RequiredArgsConstructor
public class PasswordConfirmation {

    private final PasswordHasher hasher;
    private final LoginThrottle loginThrottle;
    private final AccountMailer mailer;

    /**
     * 403 and not 401 for a wrong password: the BFF relays the core's 401 as an expired session and
     * the app would log the user out. A locked account answers as at sign-in, the attempt that
     * locks it included, so the user learns it at once and not on the next try.
     */
    public void confirm(User user, String password, String clientAddress) {
        loginThrottle.lockedUntil(user.getEmail(), clientAddress).ifPresent(until -> {
            throw new AccountLockedException(until);
        });
        if (!hasher.matches(password, user.getPasswordHash())) {
            throw failure(user, clientAddress, () -> new DomainException(ErrorType.FORBIDDEN, "auth.wrong_password",
                    "The password does not match"));
        }
    }

    /** Counts a failure of this user (wrong password or second factor) and picks the answer. */
    public DomainException failure(User user, String clientAddress, Supplier<DomainException> otherwise) {
        return loginThrottle.registerFailure(user.getEmail(), clientAddress)
                .<DomainException>map(lockout -> {
                    mailer.locked(user, lockout);
                    return new AccountLockedException(lockout.until());
                })
                .orElseGet(otherwise);
    }
}
