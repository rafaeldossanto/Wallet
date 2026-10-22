package com.wallet.core.identity.service;

import com.wallet.core.identity.dto.UserResponse;
import com.wallet.core.identity.entity.EmailTokenPurpose;
import com.wallet.core.identity.entity.User;
import com.wallet.core.identity.mapper.UserMapper;
import com.wallet.core.identity.repository.UserRepository;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * The profile (LGPD's right to correct one's data, L11): the name, freely; the e-mail, with the
 * password and only once the new address confirms it.
 */
@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final EmailLinkTokens links;
    private final PasswordConfirmation passwordConfirmation;
    private final AccountMailer mailer;
    private final TransactionTemplate transactions;

    public UserService(UserRepository userRepository, EmailLinkTokens links, PasswordConfirmation passwordConfirmation,
                       AccountMailer mailer, PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.links = links;
        this.passwordConfirmation = passwordConfirmation;
        this.mailer = mailer;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID userId) {
        return UserMapper.toResponse(existing(userId), links.pendingEmail(userId).orElse(null));
    }

    @Transactional
    public UserResponse rename(UUID userId, String displayName) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(UserService::unauthenticated);
        user.setDisplayName(displayName.strip());
        return UserMapper.toResponse(user, links.pendingEmail(userId).orElse(null));
    }

    /**
     * Sends a link to the new address; the account moves only when it is opened
     * ({@code EmailConfirmationService}), and then the old address is told.
     *
     * <p>The answer is the same whether or not the new address already has an account, as at
     * sign-up: when it has, its owner gets the sign-up notice instead and the link is never sent.
     * The request is recorded all the same, so the profile shows the same "waiting for
     * confirmation" either way.
     */
    public void requestEmailChange(UUID userId, String rawNewEmail, String password, String clientAddress) {
        User user = existing(userId);
        passwordConfirmation.confirm(user, password, clientAddress);
        String newEmail = Emails.normalize(rawNewEmail);
        if (newEmail.equals(user.getEmail())) {
            throw new DomainException(ErrorType.UNPROCESSABLE, "auth.email_unchanged", "That is already the account's e-mail");
        }
        transactions.executeWithoutResult(status -> {
            if (!links.mayIssue(userId, EmailTokenPurpose.CHANGE_EMAIL)) {
                return;
            }
            String rawToken = links.issue(userId, EmailTokenPurpose.CHANGE_EMAIL, newEmail);
            userRepository.findByEmail(newEmail).ifPresentOrElse(
                    owner -> {
                        if (owner.isEmailConfirmed()) {
                            mailer.signUpAttempt(owner);
                        }
                    },
                    () -> mailer.confirmNewEmail(newEmail, rawToken));
            log.info("[SECURITY] User {} asked to move the account to another e-mail", userId);
        });
    }

    private User existing(UUID userId) {
        return userRepository.findById(userId).orElseThrow(UserService::unauthenticated);
    }

    /** A valid token for a user that no longer exists: treat as logged out. */
    private static DomainException unauthenticated() {
        return new DomainException(ErrorType.UNAUTHORIZED, "auth.unauthenticated", "User of this token no longer exists");
    }
}
