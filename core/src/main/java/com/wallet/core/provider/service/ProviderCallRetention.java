package com.wallet.core.provider.service;

import com.wallet.core.provider.config.ProviderAuditProperties;
import com.wallet.core.provider.repository.ProviderCallRepository;
import com.wallet.core.shared.time.WalletTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Deletes provider call records older than {@code wallet.provider.audit.retention}, every night.
 * Runs on every instance: two deletes of the same rows are harmless.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderCallRetention implements SchedulingConfigurer {

    private final ProviderCallRepository repository;
    private final ProviderAuditProperties properties;
    private final Clock clock;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(new CronTask(this::deleteExpired, new CronTrigger(properties.cleanupCron(), WalletTime.ZONE)));
    }

    public int deleteExpired() {
        int deleted = repository.deleteCalledBefore(clock.instant().minus(properties.retention()));
        if (deleted > 0) {
            log.info("[PROVIDER_CALL] Deleted {} records older than {} days", deleted, properties.retention().toDays());
        }
        return deleted;
    }
}
