package com.wallet.core.sync.service;

import com.wallet.core.connection.ConnectionRegistry;
import com.wallet.core.connection.LinkedConnection;
import com.wallet.core.sync.SyncTrigger;
import com.wallet.core.sync.config.SyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * The periodic poll. Off with {@code wallet.sync.scheduler-enabled=false}, so with two instances
 * only one of them schedules (and integration tests stay quiet).
 */
@Slf4j
@Component
@ConditionalOnBooleanProperty(name = "wallet.sync.scheduler-enabled", matchIfMissing = true)
@RequiredArgsConstructor
class SyncScheduler implements SchedulingConfigurer {

    private final ConnectionRegistry registry;
    private final SyncService syncService;
    private final SyncProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new FixedDelayTask(this::syncAll, properties.interval(), properties.initialDelay()));
    }

    void syncAll() {
        for (LinkedConnection connection : registry.findSchedulable()) {
            syncService.syncNow(connection.id(), SyncTrigger.SCHEDULED);
        }
    }
}
