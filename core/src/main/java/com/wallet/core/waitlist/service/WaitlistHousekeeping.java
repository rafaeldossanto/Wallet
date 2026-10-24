package com.wallet.core.waitlist.service;

import com.wallet.core.shared.time.WalletTime;
import com.wallet.core.waitlist.config.WaitlistProperties;
import com.wallet.core.waitlist.repository.WaitlistRepository;
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
 * Every night, the sign-ups nobody confirmed after {@code pendingTtl} (7 days) are deleted (LGPD:
 * an address is kept only while it serves). Runs on every instance: two deletes of the same rows
 * are harmless.
 *
 * <p>Like {@code AccountHousekeeping}, the transaction is opened in {@link #clean}, not declared on
 * it: the scheduler runs the task registered here, which calls this object and not the Spring
 * proxy, so a {@code @Transactional} would be skipped and the delete would fail every night.
 */
@Slf4j
@Service
public class WaitlistHousekeeping implements SchedulingConfigurer {

    private final WaitlistRepository repository;
    private final WaitlistProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public WaitlistHousekeeping(WaitlistRepository repository, WaitlistProperties properties, Clock clock,
                                PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(new CronTask(this::clean, new CronTrigger(properties.cleanupCron(), WalletTime.ZONE)));
    }

    public void clean() {
        transactions.executeWithoutResult(status -> {
            int entries = repository.deletePendingCreatedBefore(clock.instant().minus(properties.pendingTtl()));
            if (entries > 0) {
                log.info("[WAITLIST] Housekeeping deleted {} sign-ups nobody confirmed", entries);
            }
        });
    }
}
