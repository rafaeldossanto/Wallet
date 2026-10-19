package com.wallet.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Where the core lives. In production it is only reachable on the internal network.
 *
 * @param gatewayKey the shared secret the core asks of every call ({@link GatewayKey}); empty
 *                   means the development key
 */
@ConfigurationProperties("wallet.core")
public record CoreProperties(
        @DefaultValue("http://localhost:8081") String url,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout,
        @DefaultValue("") String gatewayKey) {

    public String jwksUri() {
        return url + "/internal/.well-known/jwks.json";
    }
}
