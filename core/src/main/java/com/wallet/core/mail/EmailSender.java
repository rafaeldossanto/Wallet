package com.wallet.core.mail;

/**
 * The mail module's API: every module that writes to people (identity today, the waitlist next)
 * sends through here and never talks SMTP itself.
 *
 * <p>Sending is queued, not done on the caller's thread. When the caller is inside a transaction
 * the e-mail goes after the commit, so a link never reaches an inbox for a token that was rolled
 * back; then it goes on a virtual thread, so how long the SMTP server takes (or whether an e-mail
 * was sent at all) never shows in a response time. A delivery failure is logged and dropped:
 * whoever needs the e-mail again asks for it again.
 *
 * <p>Underneath, {@code SmtpMailTransport} talks to any SMTP server (Amazon SES included) and,
 * outside production without a server, {@code DevelopmentMailTransport} writes to the log.
 */
public interface EmailSender {

    void send(OutgoingEmail email);
}
