package com.wallet.core.sync.service;

import com.wallet.core.shared.time.WalletTime;
import com.wallet.core.sync.config.WebhookRetentionProperties;
import com.wallet.core.sync.repository.WebhookEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Every night, the webhook events received more than {@code wallet.retention.webhooks.events-after}
 * (30 days) ago. Runs on every instance: two deletes of the same rows are harmless.
 *
 * <p>The transaction is opened in {@link #clean}, not declared on it: the scheduler runs the task
 * registered here, which calls this object and not the Spring proxy, so a {@code @Transactional}
 * would be skipped (see {@code AccountHousekeeping}).
 */
@Slf4j
@Service
public class WebhookEventHousekeeping implements SchedulingConfigurer {

    private final WebhookEventRepository events;
    private final WebhookRetentionProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public WebhookEventHousekeeping(WebhookEventRepository events, WebhookRetentionProperties properties, Clock clock,
                                    PlatformTransactionManager transactionManager) {
        this.events = events;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(new CronTask(this::clean, new CronTrigger(properties.cleanupCron(), WalletTime.ZONE)));
    }

    public void clean() {
        transaction.executeWithoutResult(status -> {
            int deleted = events.deleteReceivedBefore(clock.instant().minus(properties.eventsAfter()));
            if (deleted > 0) {
                log.info("[RETENTION] Webhooks deleted {} events received more than {} days ago",
                        deleted, properties.eventsAfter().toDays());
            }
        });
    }
}
