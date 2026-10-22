package com.wallet.core.identity.service;

import com.wallet.core.identity.config.AuthProperties;
import com.wallet.core.identity.repository.EmailTokenRepository;
import com.wallet.core.identity.repository.LoginThrottles;
import com.wallet.core.identity.repository.UserRepository;
import com.wallet.core.shared.time.WalletTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Every night, what identity no longer needs (LGPD: keep personal data only while it serves):
 * accounts whose e-mail was never confirmed after {@code unconfirmedAccountTtl} (7 days), links
 * expired for a day, and the failed sign-in counters of scopes quiet for {@code forgetAfter}.
 * Runs on every instance: two deletes of the same rows are harmless.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountHousekeeping implements SchedulingConfigurer {

    private final UserRepository users;
    private final EmailTokenRepository emailTokens;
    private final LoginThrottles throttles;
    private final AuthProperties properties;
    private final Clock clock;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(new CronTask(this::clean, new CronTrigger(properties.cleanupCron(), WalletTime.ZONE)));
    }

    @Transactional
    public void clean() {
        Instant now = clock.instant();
        int accounts = users.deleteUnconfirmedCreatedBefore(now.minus(properties.unconfirmedAccountTtl()));
        int links = emailTokens.deleteExpiredBefore(now.minus(properties.emailLinks().ttl()));
        int counters = throttles.deleteQuietSince(now.minus(properties.lockout().forgetAfter()), now);
        if (accounts + links + counters > 0) {
            log.info("[SECURITY] Housekeeping deleted {} unconfirmed accounts, {} old links and {} quiet sign-in counters",
                    accounts, links, counters);
        }
    }
}
