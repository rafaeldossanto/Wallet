package com.wallet.bff.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** @param screenTtl how long a composed screen is reused; data only changes on a sync anyway */
@ConfigurationProperties("wallet.cache")
public record CacheProperties(@DefaultValue("60s") Duration screenTtl) {
}
