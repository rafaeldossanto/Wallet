package com.wallet.core.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param from the sender as the recipient sees it ({@code Wallet <no-reply@example.com>}). The
 *             provider must accept it: on Amazon SES, a verified address or domain
 * @param smtp the server. No host = no SMTP: outside production the e-mails go to the log
 *             ({@code DevelopmentMailTransport}); with the {@code prod} profile the core does not start
 */
@ConfigurationProperties("wallet.mail")
public record MailProperties(String from, @DefaultValue Smtp smtp) {

    /**
     * @param username both or neither with {@code password}; most providers ask for them (SES: the
     *                 SMTP credentials made for an IAM user, not its access key)
     * @param security how the connection is protected: STARTTLS on 587 (default), SSL on 465, or
     *                 NONE for a local test server; NONE is refused with the {@code prod} profile
     * @param timeout  connecting, reading and writing, each; e-mails go off the request thread, so
     *                 this bounds a stuck server, not anyone's wait
     */
    public record Smtp(
            String host,
            @DefaultValue("587") int port,
            String username,
            String password,
            @DefaultValue("STARTTLS") Security security,
            @DefaultValue("10s") Duration timeout) {
    }

    public enum Security {
        STARTTLS,
        SSL,
        NONE
    }
}
