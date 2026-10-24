package com.wallet.core.banking.service;

import com.wallet.core.banking.config.BankingRetentionProperties;
import com.wallet.core.banking.repository.AccountTransactionRepository;
import com.wallet.core.shared.time.WalletTime;
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
 * Every night, the transactions the provider stopped reporting (soft-deleted by the sync) and that
 * stayed gone for {@code wallet.retention.banking.deleted-transactions-after} (90 days). They no
 * longer show anywhere, and the sync only needs them while it may still revive one. Balances and
 * bills stay: they are the history the product shows, for as long as the account exists.
 * Runs on every instance: two deletes of the same rows are harmless.
 *
 * <p>The transaction is opened in {@link #clean}, not declared on it: the scheduler runs the task
 * registered here, which calls this object and not the Spring proxy, so a {@code @Transactional}
 * would be skipped (see {@code AccountHousekeeping}).
 */
@Slf4j
@Service
public class BankingHousekeeping implements SchedulingConfigurer {

    private final AccountTransactionRepository transactions;
    private final BankingRetentionProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public BankingHousekeeping(AccountTransactionRepository transactions, BankingRetentionProperties properties,
                               Clock clock, PlatformTransactionManager transactionManager) {
        this.transactions = transactions;
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
            int deleted = transactions.deleteRemovedBefore(clock.instant().minus(properties.deletedTransactionsAfter()));
            if (deleted > 0) {
                log.info("[RETENTION] Banking deleted {} transactions removed by the provider more than {} days ago",
                        deleted, properties.deletedTransactionsAfter().toDays());
            }
        });
    }
}
