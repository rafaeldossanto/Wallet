package com.wallet.core.mail.transport;

import com.wallet.core.mail.OutgoingEmail;
import com.wallet.core.mail.config.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.springframework.util.StringUtils.hasText;

/**
 * Plain SMTP through Spring's {@link JavaMailSender}, so any provider works: Amazon SES (the
 * natural choice on EC2), a transactional e-mail service or a company's own server. Nothing here
 * is specific to one of them.
 */
public class SmtpMailTransport implements MailTransport {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpMailTransport(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public static SmtpMailTransport of(MailProperties properties) {
        return new SmtpMailTransport(javaMailSender(properties.smtp()), properties.from().strip());
    }

    @Override
    public void deliver(OutgoingEmail email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(MailBody.of(email), false);
            if (email.hasUnsubscribeUrl()) {
                message.setHeader("List-Unsubscribe", "<" + email.unsubscribeUrl() + ">");
                message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            }
            mailSender.send(message);
        } catch (MessagingException | MailException ex) {
            throw new MailDeliveryException("SMTP did not take the " + email.kind() + " e-mail", ex);
        }
    }

    public static JavaMailSenderImpl javaMailSender(MailProperties.Smtp smtp) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.host().strip());
        sender.setPort(smtp.port());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        boolean authenticated = hasText(smtp.username());
        if (authenticated) {
            sender.setUsername(smtp.username().strip());
            sender.setPassword(smtp.password());
        }
        String timeout = String.valueOf(smtp.timeout().toMillis());
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", String.valueOf(authenticated));
        properties.put("mail.smtp.connectiontimeout", timeout);
        properties.put("mail.smtp.timeout", timeout);
        properties.put("mail.smtp.writetimeout", timeout);
        switch (smtp.security()) {
            case STARTTLS -> {
                // Required, not just enabled: a server (or someone in between) that drops the
                // STARTTLS offer must not get the password in the clear.
                properties.put("mail.smtp.starttls.enable", "true");
                properties.put("mail.smtp.starttls.required", "true");
                properties.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case SSL -> {
                properties.put("mail.smtp.ssl.enable", "true");
                properties.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case NONE -> {
                // A local test server (Mailpit, GreenMail). MailConfig refuses it in production.
            }
        }
        return sender;
    }
}
