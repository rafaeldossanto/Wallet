package com.wallet.core.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param eventsAfter a webhook event received this long ago is deleted: the record only serves to
 *                    ignore a repeated delivery and to see what came of a hint
 * @param cleanupCron when it runs, in São Paulo time
 */
@ConfigurationProperties("wallet.retention.webhooks")
public record WebhookRetentionProperties(
        @DefaultValue("30d") Duration eventsAfter,
        @DefaultValue("0 5 5 * * *") String cleanupCron) {
}
