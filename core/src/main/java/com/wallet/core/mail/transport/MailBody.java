package com.wallet.core.mail.transport;

import com.wallet.core.mail.OutgoingEmail;
import lombok.experimental.UtilityClass;

/** The text as it leaves, the same in every transport. */
@UtilityClass
public class MailBody {

    /** The e-mail's own text, and the unsubscribe link in the footer when it has one. */
    public String of(OutgoingEmail email) {
        if (!email.hasUnsubscribeUrl()) {
            return email.text();
        }
        return email.text() + "\n\n--\nNão quer mais receber estes e-mails? Cancele aqui: " + email.unsubscribeUrl() + "\n";
    }
}
