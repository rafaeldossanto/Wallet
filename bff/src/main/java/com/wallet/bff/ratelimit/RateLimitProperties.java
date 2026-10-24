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
 * <p>Linking a bank and opening the Pluggy Connect widget get low limits of their own, per user,
 * on top of the general one: each link makes the core read up to a year of the bank at Pluggy,
 * and each widget token is a call to Pluggy, all of it on the one apiKey every user shares.
 *
 * @param linkLimit          {@code POST /api/connections} per user and {@code linkWindow}
 * @param connectTokenLimit  {@code POST /api/connections/connect-token} per user and {@code connectTokenWindow}
 * @param searchLimit        text searches ({@code GET /api/transactions} with {@code q}) per user and
 *                           {@code window}: each one makes the core decrypt the period's descriptions
 * @param exportLimit        {@code GET /api/me/export} (the user's data as a file) per user and {@code exportWindow}:
 *                           each one makes the core read everything the user has
 * @param waitlistLimit      requests to {@code /api/waitlist/**} (sign-up, confirmation, unsubscribe
 *                           together) per IP and {@code waitlistWindow}
 * @param trustedProxies    the reverse proxies whose {@code X-Forwarded-For} is believed, as IPs or
 *                           CIDR ranges ({@link ClientAddress}). Empty = the connection's own address,
 *                           whatever the header says
 */
@ConfigurationProperties("wallet.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1m") Duration window,
        @DefaultValue("20") int authLimit,
        @DefaultValue("300") int defaultLimit,
        @DefaultValue("10") int linkLimit,
        @DefaultValue("1h") Duration linkWindow,
        @DefaultValue("10") int connectTokenLimit,
        @DefaultValue("1h") Duration connectTokenWindow,
        @DefaultValue("30") int searchLimit,
        @DefaultValue("3") int exportLimit,
        @DefaultValue("24h") Duration exportWindow,
        @DefaultValue("5") int waitlistLimit,
        @DefaultValue("1h") Duration waitlistWindow,
        @DefaultValue List<String> trustedProxies) {

    public RateLimitProperties {
        trustedProxies = isNull(trustedProxies) ? List.of() : List.copyOf(trustedProxies);
    }
}
