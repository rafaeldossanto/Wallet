package com.wallet.core.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param finishedRunsAfter a sync that ended this long ago is deleted, except the latest of each
 *                          connection: the "Atualizar" cooldown counts from it
 * @param cleanupCron       when it runs, in São Paulo time
 */
@ConfigurationProperties("wallet.retention.sync")
public record SyncRetentionProperties(
        @DefaultValue("90d") Duration finishedRunsAfter,
        @DefaultValue("0 55 4 * * *") String cleanupCron) {
}
