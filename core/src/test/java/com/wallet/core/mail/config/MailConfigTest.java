package com.wallet.core.mail.config;

import com.wallet.core.mail.transport.DevelopmentMailTransport;
import com.wallet.core.mail.transport.SmtpMailTransport;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailConfigTest {

    private static final String FROM = "Wallet <no-reply@wallet.test>";

    @Test
    void withoutAServerADevelopmentMachineWritesToTheLog() {
        assertThat(MailConfig.resolve(properties(FROM, null, null, null, MailProperties.Security.STARTTLS), false))
                .isInstanceOf(DevelopmentMailTransport.class);
    }

    /** A production core that cannot send would leave every new account waiting forever. */
    @Test
    void productionRefusesToStartWithoutAServerNamingTheVariable() {
        assertThatThrownBy(() -> MailConfig.resolve(properties(FROM, null, null, null, MailProperties.Security.STARTTLS), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WALLET_SMTP_HOST");
    }

    @Test
    void aServerTakesSmtp() {
        assertThat(MailConfig.resolve(properties(FROM, "email-smtp.sa-east-1.amazonaws.com", "user", "secret",
                MailProperties.Security.STARTTLS), true)).isInstanceOf(SmtpMailTransport.class);
    }

    @Test
    void aServerNeedsASender() {
        assertThatThrownBy(() -> MailConfig.resolve(properties(" ", "smtp.example.com", "user", "secret",
                MailProperties.Security.STARTTLS), false))
                .hasMessageContaining("WALLET_MAIL_FROM");
    }

    @Test
    void credentialsComeInPairs() {
        assertThatThrownBy(() -> MailConfig.resolve(properties(FROM, "smtp.example.com", "user", null,
                MailProperties.Security.STARTTLS), false))
                .hasMessageContaining("WALLET_SMTP_USERNAME and WALLET_SMTP_PASSWORD");
    }

    @Test
    void productionRefusesAConnectionInTheClear() {
        assertThatThrownBy(() -> MailConfig.resolve(properties(FROM, "smtp.example.com", "user", "secret",
                MailProperties.Security.NONE), true))
                .hasMessageContaining("WALLET_SMTP_SECURITY=NONE");
        assertThat(MailConfig.resolve(properties(FROM, "localhost", null, null, MailProperties.Security.NONE), false))
                .isInstanceOf(SmtpMailTransport.class);
    }

    private static MailProperties properties(String from, String host, String username, String password,
                                             MailProperties.Security security) {
        return new MailProperties(from, new MailProperties.Smtp(host, 587, username, password, security, Duration.ofSeconds(10)));
    }
}
