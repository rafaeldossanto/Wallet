package com.wallet.core.waitlist.service;

import com.wallet.core.mail.EmailSender;
import com.wallet.core.shared.security.ProductionProfile;
import com.wallet.core.shared.web.PublicUrl;
import com.wallet.core.waitlist.config.WaitlistProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Sends the waitlist e-mails through the mail module, and builds their links on the BFF's public
 * address ({@code WALLET_PUBLIC_URL}), like {@code AccountMailer}:
 *
 * <ul>
 *   <li>confirmation: {@code <url>/waitlist/confirm#token=...}, the token after the {@code #} so
 *       browsers never send it; the page confirms only when the button is pressed;</li>
 *   <li>unsubscribe: {@code <url>/waitlist/unsubscribe?token=...}, the token in the query because the
 *       one-click button of the mail providers (RFC 8058) posts to that exact URL.</li>
 * </ul>
 */
@Service
public class WaitlistMailer {

    static final String CONFIRMATION_PATH = "/waitlist/confirm";
    static final String UNSUBSCRIBE_PATH = "/waitlist/unsubscribe";

    private final EmailSender sender;
    private final String baseUrl;

    public WaitlistMailer(EmailSender sender, WaitlistProperties properties, Environment environment) {
        this.sender = sender;
        this.baseUrl = PublicUrl.resolve(properties.baseUrl(), ProductionProfile.isActive(environment));
    }

    /** Queued: after the caller's commit, off its thread. */
    public void confirmation(String to, String rawToken) {
        sender.send(WaitlistEmails.confirmation(to, baseUrl + CONFIRMATION_PATH + "#token=" + rawToken));
    }

    /** Queued, like {@link #confirmation}. */
    public void welcome(String to, String unsubscribeToken) {
        sender.send(WaitlistEmails.welcome(to, unsubscribeLink(unsubscribeToken)));
    }

    /** On the caller's thread: the announcement has to know whether the e-mail went. */
    public void announce(String to, AnnouncementMessage message, String unsubscribeToken) {
        sender.sendNow(WaitlistEmails.announcement(to, message, unsubscribeLink(unsubscribeToken)));
    }

    private String unsubscribeLink(String unsubscribeToken) {
        return baseUrl + UNSUBSCRIBE_PATH + "?token=" + unsubscribeToken;
    }
}
