package com.wallet.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * @param allowedOrigins where the Flutter web app is served from (CORS with credentials)
 * @param cookieSecure   the refresh cookie's Secure flag; browsers accept it on http://localhost
 */
@ConfigurationProperties("wallet.web")
public record WebProperties(
        @DefaultValue("http://localhost:5000") List<String> allowedOrigins,
        @DefaultValue("true") boolean cookieSecure) {
}
