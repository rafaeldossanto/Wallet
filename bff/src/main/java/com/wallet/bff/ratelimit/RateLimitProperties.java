package com.wallet.bff.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Fixed window per key. Generous on purpose: many phones share one public IP behind carrier NAT,
 * so the per-IP limit on login only stops floods; the core's per-account lockout stops guessing.
 */
@ConfigurationProperties("wallet.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1m") Duration window,
        @DefaultValue("20") int authLimit,
        @DefaultValue("300") int defaultLimit) {
}
