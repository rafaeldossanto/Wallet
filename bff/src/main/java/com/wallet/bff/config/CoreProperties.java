package com.wallet.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Where the core lives. In production it is only reachable on the internal network. */
@ConfigurationProperties("wallet.core")
public record CoreProperties(
        @DefaultValue("http://localhost:8081") String url,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout) {

    public String jwksUri() {
        return url + "/internal/.well-known/jwks.json";
    }
}
