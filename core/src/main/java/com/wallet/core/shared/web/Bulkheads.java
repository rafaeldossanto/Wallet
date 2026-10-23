package com.wallet.core.shared.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** One {@link Bulkhead} per expensive kind of request, sized in {@code wallet.bulkhead.*}. */
@Component
@EnableConfigurationProperties(BulkheadProperties.class)
public class Bulkheads {

    private final Bulkhead search;
    private final Bulkhead screens;
    private final Bulkhead manualSync;

    public Bulkheads(BulkheadProperties properties) {
        this.search = new Bulkhead("search", properties.search());
        this.screens = new Bulkhead("screens", properties.screens());
        this.manualSync = new Bulkhead("manual-sync", properties.manualSync());
    }

    public Bulkhead search() {
        return search;
    }

    public Bulkhead screens() {
        return screens;
    }

    public Bulkhead manualSync() {
        return manualSync;
    }
}
