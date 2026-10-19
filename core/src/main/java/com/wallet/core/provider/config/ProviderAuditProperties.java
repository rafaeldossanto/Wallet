package com.wallet.core.provider.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param retention   how long the record of a provider call is kept
 * @param cleanupCron when older records are deleted, in São Paulo time
 */
@ConfigurationProperties("wallet.provider.audit")
public record ProviderAuditProperties(
        @DefaultValue("180d") Duration retention,
        @DefaultValue("0 30 4 * * *") String cleanupCron) {
}
