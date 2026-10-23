package com.wallet.core.identity.service;

import com.wallet.core.identity.config.AuthProperties;
import com.wallet.core.identity.repository.EmailTokenRepository;
import com.wallet.core.identity.repository.LoginThrottles;
import com.wallet.core.identity.repository.UserRepository;
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
import java.time.Instant;

/**
 * Every night, what identity no longer needs (LGPD: keep personal data only while it serves):
 * accounts whose e-mail was never confirmed after {@code unconfirmedAccountTtl} (7 days), links
 * expired for a day, and the failed sign-in counters of scopes quiet for {@code forgetAfter}.
 * Runs on every instance: two deletes of the same rows are harmless.
 *
 * <p>The transaction is opened in {@link #clean}, not declared on it: the scheduler runs the task
 * registered here, which calls this object and not the Spring proxy, so a {@code @Transactional}
 * would be skipped and the deletes would fail every night for want of a transaction.
 */
@Slf4j
@Service
public class AccountHousekeeping implements SchedulingConfigurer {

    private final UserRepository users;
    private final EmailTokenRepository emailTokens;
    private final LoginThrottles throttles;
    private final AuthProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public AccountHousekeeping(UserRepository users, EmailTokenRepository emailTokens, LoginThrottles throttles,
                               AuthProperties properties, Clock clock, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.emailTokens = emailTokens;
        this.throttles = throttles;
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
            Instant now = clock.instant();
            int accounts = users.deleteUnconfirmedCreatedBefore(now.minus(properties.unconfirmedAccountTtl()));
            int links = emailTokens.deleteExpiredBefore(now.minus(properties.emailLinks().ttl()));
            int counters = throttles.deleteQuietSince(now.minus(properties.lockout().forgetAfter()), now);
            if (accounts + links + counters > 0) {
                log.info("[SECURITY] Housekeeping deleted {} unconfirmed accounts, {} old links and {} quiet sign-in counters",
                        accounts, links, counters);
            }
        });
    }
}
