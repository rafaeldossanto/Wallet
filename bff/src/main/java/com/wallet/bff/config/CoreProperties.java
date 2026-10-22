package com.wallet.bff.config;

import com.wallet.bff.client.CoreRouteGroup;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Where the core lives. In production it is only reachable on the internal network.
 *
 * @param slowReadTimeout for the calls where the core waits on the provider before answering:
 *                        deleting an account revokes every connection at Pluggy, one at a time
 * @param gatewayKey the shared secret the core asks of every call ({@link GatewayKey}); empty
 *                   means the development key
 * @param maxConcurrentCalls calls to the core in flight at once, per group of routes
 */
@ConfigurationProperties("wallet.core")
public record CoreProperties(
        @DefaultValue("http://localhost:8081") String url,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout,
        @DefaultValue("2m") Duration slowReadTimeout,
        @DefaultValue("") String gatewayKey,
        @DefaultValue CallLimits maxConcurrentCalls) {

    public String jwksUri() {
        return url + "/internal/.well-known/jwks.json";
    }

    /** One bulkhead per {@link CoreRouteGroup}. */
    public record CallLimits(
            @DefaultValue("16") int auth,
            @DefaultValue("64") int reads,
            @DefaultValue("32") int screens,
            @DefaultValue("16") int search,
            @DefaultValue("16") int provider) {

        public int of(CoreRouteGroup group) {
            return switch (group) {
                case AUTH -> auth;
                case READS -> reads;
                case SCREENS -> screens;
                case SEARCH -> search;
                case PROVIDER -> provider;
            };
        }
    }
}
