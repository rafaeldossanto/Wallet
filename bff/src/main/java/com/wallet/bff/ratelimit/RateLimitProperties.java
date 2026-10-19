package com.wallet.bff.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

import static java.util.Objects.isNull;

/**
 * Fixed window per key. Generous on purpose: many phones share one public IP behind carrier NAT,
 * so the per-IP limit on login only stops floods; the core's per-account lockout stops guessing.
 *
 * @param trustedProxies the reverse proxies whose {@code X-Forwarded-For} is believed, as IPs or
 *                       CIDR ranges ({@link ClientAddress}). Empty = the connection's own address,
 *                       whatever the header says
 */
@ConfigurationProperties("wallet.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1m") Duration window,
        @DefaultValue("20") int authLimit,
        @DefaultValue("300") int defaultLimit,
        @DefaultValue List<String> trustedProxies) {

    public RateLimitProperties {
        trustedProxies = isNull(trustedProxies) ? List.of() : List.copyOf(trustedProxies);
    }
}
