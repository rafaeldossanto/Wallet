package com.wallet.core.mail.transport;

import com.wallet.core.mail.EmailAddresses;
import com.wallet.core.mail.OutgoingEmail;
import lombok.extern.slf4j.Slf4j;

/**
 * Outside production, without an SMTP server: the e-mail goes to the log, so a confirmation link
 * can be opened on a development machine.
 *
 * <p>The only place in the Wallet where a token (inside a link) reaches a log. {@code MailConfig}
 * never picks it with the {@code prod} profile, and every line says it is for development only.
 */
@Slf4j
public class DevelopmentMailTransport implements MailTransport {

    @Override
    public void deliver(OutgoingEmail email) {
        log.warn("""
                [MAIL] DEVELOPMENT ONLY, not sent: no SMTP server configured (WALLET_SMTP_HOST). \
                It may carry a one-time link; never run like this in production.
                To: {}
                Subject: {}
                {}""", EmailAddresses.mask(email.to()), email.subject(), MailBody.of(email));
    }
}
