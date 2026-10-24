package com.wallet.core.banking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * What banking stops keeping (LGPD: personal data only while it serves). The balance history stays
 * for as long as the account does: it is what the product shows.
 *
 * @param deletedTransactionsAfter a transaction the provider stopped reporting (soft-deleted) is
 *                                 deleted for good this long after
 * @param cleanupCron              when it runs, in São Paulo time
 */
@ConfigurationProperties("wallet.retention.banking")
public record BankingRetentionProperties(
        @DefaultValue("90d") Duration deletedTransactionsAfter,
        @DefaultValue("0 50 4 * * *") String cleanupCron) {
}
