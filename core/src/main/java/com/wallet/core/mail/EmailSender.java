package com.wallet.core.mail;

/**
 * The mail module's API: every module that writes to people (identity, the waitlist) sends through
 * here and never talks SMTP itself.
 *
 * <p>{@link #send} is queued, not done on the caller's thread. When the caller is inside a transaction
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

    /**
     * Sends on the caller's thread and returns when the server took the message, or throws when it
     * did not. For whoever has to know the result, or exits when done (the opening announcement:
     * a queued e-mail would be lost with the process, and a failed one must not count as sent).
     * Never call it inside a transaction or holding a database connection: it waits on the network.
     *
     * @throws com.wallet.core.mail.transport.MailDeliveryException when the server refused the
     *         message or could not be reached
     */
    void sendNow(OutgoingEmail email);
}
