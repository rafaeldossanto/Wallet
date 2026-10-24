package com.wallet.core.identity.service;

import com.wallet.core.identity.dto.EmailConfirmationResponse;
import com.wallet.core.identity.entity.EmailToken;
import com.wallet.core.identity.entity.EmailTokenPurpose;
import com.wallet.core.identity.entity.User;
import com.wallet.core.identity.repository.UserRepository;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

import static java.util.Objects.nonNull;

/**
 * Opens an e-mailed link: confirms a new account, or moves an account to the address the link was
 * sent to. Only someone who reads that inbox can do either.
 */
@Slf4j
@Service
public class EmailConfirmationService {

    private final EmailLinkTokens links;
    private final UserRepository users;
    private final AccountMailer mailer;
    private final LoginThrottle loginThrottle;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public EmailConfirmationService(EmailLinkTokens links, UserRepository users, AccountMailer mailer,
                                    LoginThrottle loginThrottle, Clock clock, PlatformTransactionManager transactionManager) {
        this.links = links;
        this.users = users;
        this.mailer = mailer;
        this.loginThrottle = loginThrottle;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public EmailConfirmationResponse confirm(String rawToken) {
        Confirmed confirmed = transactions.execute(status -> {
            EmailToken token = links.consume(rawToken, EmailTokenPurpose.CONFIRM_ACCOUNT, EmailTokenPurpose.CHANGE_EMAIL)
                    .orElseThrow(EmailConfirmationService::linkInvalid);
            User user = users.findByIdForUpdate(token.getUserId()).orElseThrow(EmailConfirmationService::linkInvalid);
            return switch (token.getPurpose()) {
                case CONFIRM_ACCOUNT -> confirmAccount(user);
                case CHANGE_EMAIL -> changeEmail(user, token.getNewEmail());
                case PASSWORD_RESET -> throw linkInvalid();
            };
        });
        if (EmailConfirmationResponse.Result.ACCOUNT_CONFIRMED.equals(confirmed.result())) {
            // Sign-ins tried before the confirmation failed like wrong passwords: they no longer count.
            loginThrottle.forgetAccount(confirmed.email());
        }
        return new EmailConfirmationResponse(confirmed.result());
    }

    private Confirmed confirmAccount(User user) {
        if (!user.isEmailConfirmed()) {
            user.setEmailConfirmedAt(clock.instant());
            log.info("[SECURITY] User {} confirmed the e-mail of the account", user.getId());
        }
        return new Confirmed(EmailConfirmationResponse.Result.ACCOUNT_CONFIRMED, user.getEmail());
    }

    /**
     * The address may have been taken since the link was sent: then nothing moves, and whoever
     * opened the link (who reads that inbox) is told it already has an account.
     */
    private Confirmed changeEmail(User user, String newEmail) {
        if (users.existsByEmail(newEmail)) {
            throw emailTaken();
        }
        String oldEmail = user.getEmail();
        user.setEmail(newEmail);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw emailTaken();
        }
        Instant now = clock.instant();
        if (nonNull(oldEmail)) {
            mailer.emailChanged(oldEmail, newEmail, now);
        }
        log.info("[SECURITY] User {} moved the account to a new e-mail", user.getId());
        return new Confirmed(EmailConfirmationResponse.Result.EMAIL_CHANGED, newEmail);
    }

    private static DomainException linkInvalid() {
        return new DomainException(ErrorType.UNPROCESSABLE, "auth.link_invalid",
                "The link is unknown, expired, already used or replaced by a newer one");
    }

    private static DomainException emailTaken() {
        return new DomainException(ErrorType.CONFLICT, "auth.email_taken", "E-mail already belongs to an account");
    }

    private record Confirmed(EmailConfirmationResponse.Result result, String email) {
    }
}
