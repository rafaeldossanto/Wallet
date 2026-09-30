package com.wallet.core.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param firstWindow   history read on the first sync; the Open Finance guarantees 12 months
 * @param overlap       how far before the latest known transaction each sync re-reads. Pluggy
 *                      re-collects 4 days back; 7 covers that with room to spare
 * @param staleRunAfter a RUNNING sync older than this is assumed dead (instance crashed) and released
 */
@ConfigurationProperties("wallet.sync")
public record SyncProperties(
        @DefaultValue("6h") Duration interval,
        @DefaultValue("1m") Duration initialDelay,
        @DefaultValue("true") boolean schedulerEnabled,
        @DefaultValue("15m") Duration manualCooldown,
        @DefaultValue("365d") Duration firstWindow,
        @DefaultValue("7d") Duration overlap,
        @DefaultValue("15m") Duration staleRunAfter) {
}
