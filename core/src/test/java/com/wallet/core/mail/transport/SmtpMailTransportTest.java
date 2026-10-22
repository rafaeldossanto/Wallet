package com.wallet.core.mail.transport;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.wallet.core.mail.OutgoingEmail;
import com.wallet.core.mail.config.MailProperties;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A real SMTP conversation with an in-memory server (GreenMail), authentication included. */
class SmtpMailTransportTest {

    @RegisterExtension
    static final GreenMailExtension SERVER = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort())
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("wallet-smtp", "smtp-secret"));

    @Test
    void sendsAPlainTextMessageInUtf8() throws Exception {
        transport("wallet-smtp", "smtp-secret").deliver(OutgoingEmail.of("rafael@example.com",
                "Confirme seu e-mail no Wallet", "Olá! Abra o link: https://wallet.test/email/confirm#token=abc", "test"));

        MimeMessage received = SERVER.getReceivedMessages()[0];
        assertThat(received.getSubject()).isEqualTo("Confirme seu e-mail no Wallet");
        assertThat(((InternetAddress) received.getFrom()[0]).getAddress()).isEqualTo("no-reply@wallet.test");
        assertThat(((InternetAddress) received.getFrom()[0]).getPersonal()).isEqualTo("Wallet");
        assertThat(received.getAllRecipients()).extracting(Object::toString).containsExactly("rafael@example.com");
        assertThat(received.getContentType()).startsWith("text/plain").containsIgnoringCase("charset=UTF-8");
        assertThat(GreenMailUtil.getBody(received)).isNotBlank();
        assertThat((String) received.getContent()).contains("Olá!").contains("#token=abc");
        assertThat(received.getHeader("List-Unsubscribe")).isNull();
    }

    /** Ready for the e-mails people subscribe to: the link in the footer and the one-click headers. */
    @Test
    void anUnsubscribeLinkGoesInTheFooterAndTheHeaders() throws Exception {
        String unsubscribe = "https://wallet.test/waitlist/unsubscribe?token=xyz";

        transport("wallet-smtp", "smtp-secret").deliver(OutgoingEmail.of("rafael@example.com", "O Wallet abriu",
                "Chegou a sua vez.", "waitlist-opening").withUnsubscribeUrl(unsubscribe));

        MimeMessage received = SERVER.getReceivedMessages()[0];
        assertThat((String) received.getContent()).contains("Chegou a sua vez.").contains("Cancele aqui: " + unsubscribe);
        assertThat(received.getHeader("List-Unsubscribe")).containsExactly("<" + unsubscribe + ">");
        assertThat(received.getHeader("List-Unsubscribe-Post")).containsExactly("List-Unsubscribe=One-Click");
    }

    /** The failure names only the kind of e-mail: the server's own message may carry the address. */
    @Test
    void aRefusalNamesOnlyTheKindOfEmail() {
        SmtpMailTransport wrongPassword = transport("wallet-smtp", "not-the-secret");

        assertThatThrownBy(() -> wrongPassword.deliver(OutgoingEmail.of("rafael@example.com", "Assunto", "Texto",
                "account-confirmation")))
                .isInstanceOf(MailDeliveryException.class)
                .hasMessage("SMTP did not take the account-confirmation e-mail");
        assertThat(SERVER.getReceivedMessages()).isEmpty();
    }

    @Test
    void startTlsIsRequiredNotJustOffered() {
        var sender = SmtpMailTransport.javaMailSender(smtp(MailProperties.Security.STARTTLS, "user", "secret"));

        assertThat(sender.getJavaMailProperties())
                .containsEntry("mail.smtp.auth", "true")
                .containsEntry("mail.smtp.starttls.enable", "true")
                .containsEntry("mail.smtp.starttls.required", "true")
                .containsEntry("mail.smtp.ssl.checkserveridentity", "true")
                .containsEntry("mail.smtp.timeout", "10000");
        assertThat(sender.getPort()).isEqualTo(587);
    }

    private static SmtpMailTransport transport(String username, String password) {
        MailProperties.Smtp smtp = new MailProperties.Smtp("127.0.0.1", SERVER.getSmtp().getPort(), username, password,
                MailProperties.Security.NONE, Duration.ofSeconds(5));
        return SmtpMailTransport.of(new MailProperties("Wallet <no-reply@wallet.test>", smtp));
    }

    private static MailProperties.Smtp smtp(MailProperties.Security security, String username, String password) {
        return new MailProperties.Smtp("smtp.example.com", 587, username, password, security, Duration.ofSeconds(10));
    }
}
