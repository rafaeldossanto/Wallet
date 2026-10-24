package com.wallet.core.identity.service;

import com.wallet.core.identity.entity.EmailToken;
import com.wallet.core.identity.entity.EmailTokenPurpose;
import com.wallet.core.identity.entity.User;
import com.wallet.core.identity.repository.UserRepository;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * "Esqueci minha senha" and "Trocar senha". Both end the same way: a new BCrypt hash, every refresh
 * token of the account revoked (all sessions end, the one that asked included), the moment of the
 * change recorded ({@code password_changed_at}: the access tokens already out are refused from then
 * on, see {@code ActiveUserInterceptor}) and a notice to the owner, with no link in it.
 *
 * <p>The reset is what closes the sign-up hole of finding A16: someone who signs up with the
 * victim's e-mail and waits for the victim to confirm it holds an account with their own password,
 * and the victim takes it back by choosing a new one. The second factor stays on: a link in an
 * inbox is not an authenticator.
 *
 * <p>Like {@link RegistrationService}, the new password is hashed before any database work, so no
 * connection is held while BCrypt waits for its turn.
 */
@Slf4j
@Service
public class PasswordService {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final PasswordConfirmation passwordConfirmation;
    private final EmailLinkTokens links;
    private final RefreshTokenService refreshTokens;
    private final LoginThrottle loginThrottle;
    private final AccountMailer mailer;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PasswordService(UserRepository users, PasswordHasher hasher, PasswordConfirmation passwordConfirmation,
                           EmailLinkTokens links, RefreshTokenService refreshTokens, LoginThrottle loginThrottle,
                           AccountMailer mailer, Clock clock, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.hasher = hasher;
        this.passwordConfirmation = passwordConfirmation;
        this.links = links;
        this.refreshTokens = refreshTokens;
        this.loginThrottle = loginThrottle;
        this.mailer = mailer;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * A link for a confirmed account, within the hourly limit; for any other e-mail, nothing. The
     * answer is the same either way (finding A16), and an unconfirmed account gets no link: the
     * sign-up one is the only door it has.
     */
    public void forgot(String rawEmail) {
        transactions.executeWithoutResult(status -> users.findByEmailForUpdate(Emails.normalize(rawEmail))
                .filter(User::isEmailConfirmed)
                .filter(user -> links.mayIssue(user.getId(), EmailTokenPurpose.PASSWORD_RESET))
                .ifPresent(user -> {
                    mailer.passwordReset(user, links.issue(user.getId(), EmailTokenPurpose.PASSWORD_RESET, null));
                    log.info("[SECURITY] User {} asked for a password reset link", user.getId());
                }));
    }

    /**
     * Opens the link: sets the new password and spends the link. A link that is unknown, expired,
     * used, replaced or of another kind is {@code auth.link_invalid}, and nothing changes.
     *
     * @param clientAddress where the page's request came from: that address's count of wrong
     *                      sign-ins starts over too, with the account's
     */
    public void reset(String rawToken, String newPassword, String clientAddress) {
        String passwordHash = hasher.encode(newPassword);
        Instant now = clock.instant();
        User changed = transactions.execute(status -> {
            EmailToken token = links.consume(rawToken, EmailTokenPurpose.PASSWORD_RESET)
                    .orElseThrow(PasswordService::linkInvalid);
            User user = users.findByIdForUpdate(token.getUserId()).orElseThrow(PasswordService::linkInvalid);
            replacePassword(user, passwordHash, now);
            log.info("[SECURITY] User {} chose a new password with the reset link", user.getId());
            return user;
        });
        // Counters are the failed sign-ins of before: the owner must not stay locked out of the new password.
        loginThrottle.forgetAccount(changed.getEmail());
        loginThrottle.registerSuccess(changed.getEmail(), clientAddress);
    }

    /**
     * The password again first, as for deleting the account: a wrong one is 403 and counts toward
     * the lockout. The new one must differ from the current one.
     */
    public void change(UUID userId, String currentPassword, String newPassword, String clientAddress) {
        User user = users.findById(userId).orElseThrow(PasswordService::unauthenticated);
        passwordConfirmation.confirm(user, currentPassword, clientAddress);
        if (newPassword.equals(currentPassword)) {
            throw new DomainException(ErrorType.VALIDATION, "validation.failed",
                    "The new password must differ from the current one");
        }
        String passwordHash = hasher.encode(newPassword);
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> {
            User locked = users.findByIdForUpdate(userId).orElseThrow(PasswordService::unauthenticated);
            replacePassword(locked, passwordHash, now);
            log.info("[SECURITY] User {} changed the password", userId);
        });
    }

    private void replacePassword(User user, String passwordHash, Instant now) {
        user.setPasswordHash(passwordHash);
        user.setPasswordChangedAt(now);
        refreshTokens.revokeAllOf(user.getId());
        mailer.passwordChanged(user.getEmail(), now);
    }

    /** 422 and the code of the e-mail confirmation, which the pages and the app already speak. */
    private static DomainException linkInvalid() {
        return new DomainException(ErrorType.UNPROCESSABLE, "auth.link_invalid",
                "The link is unknown, expired, already used or replaced by a newer one");
    }

    private static DomainException unauthenticated() {
        return new DomainException(ErrorType.UNAUTHORIZED, "auth.unauthenticated", "User of this token no longer exists");
    }
}
