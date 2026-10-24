package com.wallet.core.identity.service;

import com.wallet.core.identity.config.AuthProperties;
import com.wallet.core.identity.entity.User;
import com.wallet.core.identity.repository.EmailNotices;
import com.wallet.core.mail.EmailSender;
import com.wallet.core.shared.security.ProductionProfile;
import com.wallet.core.shared.web.PublicUrl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Sends the account e-mails through the mail module, and builds their links.
 *
 * <p>A link points at the BFF's confirmation page, {@code <WALLET_PUBLIC_URL>/email/confirm#token=...}.
 * The token goes after the {@code #}: browsers never send that part, so it stays out of the access
 * logs of the BFF and of any proxy in front of it. The page posts it to the BFF when the person
 * presses the button, which also keeps mail scanners that open every link from confirming anything.
 *
 * <p>Notices (a sign-up attempt with a taken e-mail, a lockout) go at most once per account per
 * {@code noticeInterval}, so nobody can flood an inbox through the Wallet.
 */
@Slf4j
@Service
public class AccountMailer {

    static final String CONFIRMATION_PATH = "/email/confirm";
    static final String PASSWORD_RESET_PATH = "/password/reset";

    private final EmailSender sender;
    private final EmailNotices notices;
    private final AuthProperties properties;
    private final Clock clock;
    private final String baseUrl;

    public AccountMailer(EmailSender sender, EmailNotices notices, AuthProperties properties, Clock clock,
                         Environment environment) {
        this.sender = sender;
        this.notices = notices;
        this.properties = properties;
        this.clock = clock;
        this.baseUrl = PublicUrl.resolve(properties.emailLinks().baseUrl(), ProductionProfile.isActive(environment));
    }

    public void confirmAccount(User user, String rawToken) {
        sender.send(AccountEmails.confirmAccount(user.getEmail(), link(CONFIRMATION_PATH, rawToken)));
    }

    public void confirmNewEmail(String newEmail, String rawToken) {
        sender.send(AccountEmails.confirmNewEmail(newEmail, link(CONFIRMATION_PATH, rawToken)));
    }

    /** The link of "Esqueci minha senha": {@code <WALLET_PUBLIC_URL>/password/reset#token=...}, the token after the {@code #} like the others. */
    public void passwordReset(User user, String rawToken) {
        sender.send(AccountEmails.passwordReset(user.getEmail(), link(PASSWORD_RESET_PATH, rawToken)));
    }

    /** After a password reset or change: no link, and it goes whatever the notice interval. */
    public void passwordChanged(String email, Instant when) {
        sender.send(AccountEmails.passwordChanged(email, when));
    }

    public void emailChanged(String oldEmail, String newEmail, Instant when) {
        sender.send(AccountEmails.emailChanged(oldEmail, newEmail, when));
    }

    /** Someone tried to sign up (or move another account) with this account's e-mail. */
    public void signUpAttempt(User user) {
        if (claim(user, "SIGN_UP_ATTEMPT")) {
            sender.send(AccountEmails.signUpAttempt(user.getEmail()));
        }
    }

    public void locked(User user, LoginThrottle.Lockout lockout) {
        if (user.isEmailConfirmed() && claim(user, "ACCOUNT_LOCKED")) {
            log.info("[SECURITY] Telling user {} the sign-in is locked ({})", user.getId(), lockout.scope());
            sender.send(AccountEmails.locked(user.getEmail(), lockout));
        }
    }

    private boolean claim(User user, String kind) {
        Instant now = clock.instant();
        return notices.claim(user.getId(), kind, now, now.minus(properties.noticeInterval()));
    }

    private String link(String path, String rawToken) {
        return baseUrl + path + "#token=" + rawToken;
    }
}
