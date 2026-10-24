package com.wallet.core.waitlist.service;

import com.wallet.core.mail.OutgoingEmail;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** What the waitlist e-mails say: short, in Portuguese, and nothing a stranger typed. */
class WaitlistEmailsTest {

    @Test
    void theConfirmationCarriesTheLinkTheValidityAndTheWayOut() {
        OutgoingEmail email = WaitlistEmails.confirmation("rafael@example.com", "https://wallet.test/waitlist/confirm#token=abc");

        assertThat(email.to()).isEqualTo("rafael@example.com");
        assertThat(email.subject()).isEqualTo("Confirme seu e-mail na lista do Wallet");
        assertThat(email.kind()).isEqualTo("waitlist-confirmation");
        assertThat(email.text()).contains("https://wallet.test/waitlist/confirm#token=abc")
                .contains("24 horas").contains("ignorar");
        assertThat(email.hasUnsubscribeUrl()).isFalse();
    }

    @Test
    void theWelcomeLeavesTheUnsubscribeLinkToTheFooterAndTheHeaders() {
        OutgoingEmail email = WaitlistEmails.welcome("rafael@example.com", "https://wallet.test/waitlist/unsubscribe?token=abc");

        assertThat(email.subject()).isEqualTo("Você está na lista do Wallet");
        assertThat(email.kind()).isEqualTo("waitlist-welcome");
        assertThat(email.unsubscribeUrl()).isEqualTo("https://wallet.test/waitlist/unsubscribe?token=abc");
    }

    @Test
    void theAnnouncementIsTheFileAsWrittenWithTheRecipientsOwnUnsubscribeLink() {
        OutgoingEmail email = WaitlistEmails.announcement("rafael@example.com",
                new AnnouncementMessage("O Wallet abriu", "Já dá para entrar."), "https://wallet.test/waitlist/unsubscribe?token=abc");

        assertThat(email.subject()).isEqualTo("O Wallet abriu");
        assertThat(email.text()).isEqualTo("Já dá para entrar.");
        assertThat(email.kind()).isEqualTo("waitlist-announcement");
        assertThat(email.unsubscribeUrl()).isEqualTo("https://wallet.test/waitlist/unsubscribe?token=abc");
    }
}
