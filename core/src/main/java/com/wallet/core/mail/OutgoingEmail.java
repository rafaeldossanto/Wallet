package com.wallet.core.mail;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

/**
 * A plain-text e-mail. Plain on purpose: it reads the same in every client, and there is no HTML
 * for a name or a link to break out of.
 *
 * @param kind           what the message is ({@code account-confirmation}), for the logs: they
 *                       never carry the address or the text
 * @param unsubscribeUrl for e-mails people subscribe to (the waitlist's announcements): the
 *                       transport adds the link to the footer and the {@code List-Unsubscribe}
 *                       headers (RFC 2369, and RFC 8058's one-click POST to the same URL), which
 *                       large providers ask of bulk senders. {@code null} for account e-mails,
 *                       which nobody can opt out of
 */
public record OutgoingEmail(String to, String subject, String text, String kind, String unsubscribeUrl) {

    public OutgoingEmail {
        requireNonNull(to, "to");
        requireNonNull(subject, "subject");
        requireNonNull(text, "text");
        requireNonNull(kind, "kind");
    }

    public static OutgoingEmail of(String to, String subject, String text, String kind) {
        return new OutgoingEmail(to, subject, text, kind, null);
    }

    public OutgoingEmail withUnsubscribeUrl(String url) {
        return new OutgoingEmail(to, subject, text, kind, url);
    }

    public boolean hasUnsubscribeUrl() {
        return !isNull(unsubscribeUrl);
    }
}
