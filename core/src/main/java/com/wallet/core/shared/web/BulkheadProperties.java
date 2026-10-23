package com.wallet.core.shared.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import static java.util.Objects.isNull;

/**
 * The expensive kinds of request and how many of each run at once ({@link Bulkhead}). The search
 * and the screens hold a database connection while they run: together they stay below
 * {@code spring.datasource.hikari.maximum-pool-size}, so logins, the cheap reads and the sync's
 * writes always find one.
 *
 * @param search     the statement's text search: every description of the period is decrypted
 * @param screens    reads that add up long periods or several modules (overview, net worth
 *                   history, the month's plan, fixed expense suggestions)
 * @param manualSync the "Atualizar" button's syncs, held until the background run ends: each one
 *                   calls Pluggy several times and writes in the database
 */
@ConfigurationProperties("wallet.bulkhead")
public record BulkheadProperties(Limits search, Limits screens, Limits manualSync) {

    public BulkheadProperties {
        search = isNull(search) ? new Limits(3, 2) : search;
        screens = isNull(screens) ? new Limits(5, 3) : screens;
        manualSync = isNull(manualSync) ? new Limits(4, 2) : manualSync;
    }

    public record Limits(int maxConcurrent, int maxPerUser) {
    }
}
