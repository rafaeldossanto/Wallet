package com.wallet.core.mail.config;

import com.wallet.core.mail.transport.DevelopmentMailTransport;
import com.wallet.core.mail.transport.MailTransport;
import com.wallet.core.mail.transport.SmtpMailTransport;
import com.wallet.core.shared.security.ProductionProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import static org.springframework.util.StringUtils.hasText;

/**
 * Picks the transport at startup. With an SMTP host, SMTP. Without one, outside production, the
 * log ({@link DevelopmentMailTransport}). With the {@code prod} profile a missing or unsafe setting
 * stops the boot naming the variable: a server that "works" without sending would leave every new
 * account waiting for a confirmation that never arrives.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    MailTransport mailTransport(MailProperties properties, Environment environment) {
        return resolve(properties, ProductionProfile.isActive(environment));
    }

    static MailTransport resolve(MailProperties properties, boolean production) {
        MailProperties.Smtp smtp = properties.smtp();
        if (!hasText(smtp.host())) {
            if (production) {
                throw new IllegalStateException("WALLET_SMTP_HOST is required with the prod profile: without it "
                        + "no e-mail leaves (account confirmation, notices). Set WALLET_SMTP_HOST, "
                        + "WALLET_SMTP_USERNAME, WALLET_SMTP_PASSWORD and WALLET_MAIL_FROM");
            }
            log.warn("No WALLET_SMTP_HOST configured: e-mails go to the log, links included. Development only.");
            return new DevelopmentMailTransport();
        }
        if (!hasText(properties.from())) {
            throw new IllegalStateException("WALLET_MAIL_FROM is required with WALLET_SMTP_HOST: "
                    + "a sender the provider accepts, e.g. Wallet <no-reply@example.com>");
        }
        if (hasText(smtp.username()) != hasText(smtp.password())) {
            throw new IllegalStateException("Set both WALLET_SMTP_USERNAME and WALLET_SMTP_PASSWORD, or neither");
        }
        if (production && MailProperties.Security.NONE.equals(smtp.security())) {
            throw new IllegalStateException("WALLET_SMTP_SECURITY=NONE is refused with the prod profile: "
                    + "use STARTTLS (port 587) or SSL (port 465)");
        }
        log.info("[MAIL] SMTP at {}:{} ({})", smtp.host().strip(), smtp.port(), smtp.security());
        return SmtpMailTransport.of(properties);
    }
}
