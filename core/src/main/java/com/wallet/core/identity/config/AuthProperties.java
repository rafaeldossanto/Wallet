package com.wallet.core.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param refreshReuseTolerance how long after a rotation the previous refresh token is still
 *                              accepted, for the device whose response was lost (reload, Wi-Fi drop)
 */
@ConfigurationProperties("wallet.auth")
public record AuthProperties(
        @DefaultValue("5") int maxFailedLogins,
        @DefaultValue("15m") Duration lockoutDuration,
        @DefaultValue("1m") Duration refreshReuseTolerance) {
}
