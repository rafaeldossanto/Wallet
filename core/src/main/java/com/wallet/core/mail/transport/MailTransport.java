package com.wallet.core.mail.transport;

import com.wallet.core.mail.OutgoingEmail;

/** How an e-mail actually leaves: SMTP, or the log on a development machine. Picked by {@code MailConfig}. */
public interface MailTransport {

    /** @throws MailDeliveryException when the server refused the message or could not be reached */
    void deliver(OutgoingEmail email);
}
