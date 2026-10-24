package com.wallet.core.sync.service;

import com.wallet.core.shared.time.WalletTime;
import com.wallet.core.sync.config.SyncRetentionProperties;
import com.wallet.core.sync.repository.SyncRunRepository;
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
 * Every night, the record of syncs that ended more than {@code wallet.retention.sync.finished-runs-after}
 * (90 days) ago. The latest finished sync of each connection always stays: the "Atualizar" cooldown
 * counts from it. A running sync is never touched.
 * Runs on every instance: two deletes of the same rows are harmless.
 *
 * <p>The transaction is opened in {@link #clean}, not declared on it: the scheduler runs the task
 * registered here, which calls this object and not the Spring proxy, so a {@code @Transactional}
 * would be skipped (see {@code AccountHousekeeping}).
 */
@Slf4j
@Service
public class SyncRunHousekeeping implements SchedulingConfigurer {

    private final SyncRunRepository runs;
    private final SyncRetentionProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public SyncRunHousekeeping(SyncRunRepository runs, SyncRetentionProperties properties, Clock clock,
                               PlatformTransactionManager transactionManager) {
        this.runs = runs;
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
            int deleted = runs.deleteFinishedBeforeKeepingLatest(clock.instant().minus(properties.finishedRunsAfter()));
            if (deleted > 0) {
                log.info("[RETENTION] Sync deleted {} runs that ended more than {} days ago",
                        deleted, properties.finishedRunsAfter().toDays());
            }
        });
    }
}
