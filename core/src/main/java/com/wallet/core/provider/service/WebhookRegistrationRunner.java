package com.wallet.core.provider.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * The command that registers the Wallet's webhooks at the provider, run on the server in place of
 * the API (like the waitlist's announcement). It only exists with {@code wallet.webhooks.register=true};
 * it registers, logs what it did and exits. A missing secret or address stops it with the variable's name.
 *
 * <pre>
 * java -jar core.jar --spring.main.web-application-type=none --wallet.webhooks.register=true
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wallet.webhooks", name = "register", havingValue = "true")
public class WebhookRegistrationRunner implements ApplicationRunner {

    private final WebhookRegistration registration;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        WebhookRegistration.Report report = registration.register();
        log.info("[WEBHOOK] Done: {} created, {} updated", report.created(), report.updated());
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
