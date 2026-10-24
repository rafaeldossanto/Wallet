package com.wallet.core.waitlist.service;

import com.wallet.core.mail.OutgoingEmail;
import lombok.experimental.UtilityClass;

/**
 * The texts of the waitlist e-mails, in Portuguese, like the account's ({@code AccountEmails}).
 * Short and plain, with nothing a stranger typed in a form: whoever signs up with someone else's
 * address cannot put words in a message from the Wallet.
 */
@UtilityClass
public class WaitlistEmails {

    private static final String SIGNATURE = "\n\nWallet\n";

    public OutgoingEmail confirmation(String to, String link) {
        return OutgoingEmail.of(to, "Confirme seu e-mail na lista do Wallet", """
                Para entrar na lista de espera do Wallet, confirme o seu e-mail abrindo o link abaixo. Ele vale por 24 horas:

                %s

                Se você não pediu isso, é só ignorar este e-mail.""".formatted(link) + SIGNATURE, "waitlist-confirmation");
    }

    /** The unsubscribe link goes in the footer and the headers the transport adds. */
    public OutgoingEmail welcome(String to, String unsubscribeLink) {
        return OutgoingEmail.of(to, "Você está na lista do Wallet", """
                Pronto, seu e-mail está confirmado e você está na lista de espera do Wallet.
                Avisamos por aqui assim que o Wallet abrir.""" + SIGNATURE, "waitlist-welcome")
                .withUnsubscribeUrl(unsubscribeLink);
    }

    public OutgoingEmail announcement(String to, AnnouncementMessage message, String unsubscribeLink) {
        return OutgoingEmail.of(to, message.subject(), message.body(), "waitlist-announcement")
                .withUnsubscribeUrl(unsubscribeLink);
    }
}
