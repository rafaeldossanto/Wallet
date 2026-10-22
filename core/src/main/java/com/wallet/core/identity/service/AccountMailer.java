package com.wallet.core.identity.service;

import com.wallet.core.identity.config.AuthProperties;
import com.wallet.core.identity.entity.User;
import com.wallet.core.identity.repository.EmailNotices;
import com.wallet.core.mail.EmailSender;
import com.wallet.core.shared.security.ProductionProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import static org.springframework.util.StringUtils.hasText;

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
    private static final String DEVELOPMENT_BASE_URL = "http://localhost:8080";

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
        this.baseUrl = resolveBaseUrl(properties.emailLinks().baseUrl(), ProductionProfile.isActive(environment));
    }

    public void confirmAccount(User user, String rawToken) {
        sender.send(AccountEmails.confirmAccount(user.getEmail(), link(rawToken)));
    }

    public void confirmNewEmail(String newEmail, String rawToken) {
        sender.send(AccountEmails.confirmNewEmail(newEmail, link(rawToken)));
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

    private String link(String rawToken) {
        return baseUrl + CONFIRMATION_PATH + "#token=" + rawToken;
    }

    /**
     * Required with the {@code prod} profile, and https there: a link to localhost, or one sent in the
     * clear, would leave every new account unconfirmed or hand the token to the network.
     */
    static String resolveBaseUrl(String configured, boolean production) {
        if (!hasText(configured)) {
            if (production) {
                throw new IllegalStateException("WALLET_PUBLIC_URL is required with the prod profile: the e-mails "
                        + "link to the BFF's public address (e.g. https://wallet.example.com)");
            }
            return DEVELOPMENT_BASE_URL;
        }
        String url = configured.strip().replaceAll("/+$", "");
        URI uri = URI.create(url);
        if (!hasText(uri.getHost()) || !hasText(uri.getScheme())) {
            throw new IllegalStateException("WALLET_PUBLIC_URL must be an absolute URL, e.g. https://wallet.example.com");
        }
        if (production && !"https".equals(uri.getScheme().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("WALLET_PUBLIC_URL must be https with the prod profile: "
                    + "the e-mailed links carry one-time tokens");
        }
        return url;
    }
}
