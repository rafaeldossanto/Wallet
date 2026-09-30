package com.wallet.core.provider.pluggy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

import static org.springframework.util.StringUtils.hasText;

/**
 * @param apiKeyTtl how long Pluggy's apiKey lives (2 h). It is renewed a few minutes before that.
 */
@ConfigurationProperties("wallet.pluggy")
public record PluggyProperties(
        @DefaultValue("https://api.pluggy.ai") String baseUrl,
        String clientId,
        String clientSecret,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue("2h") Duration apiKeyTtl) {

    public boolean hasCredentials() {
        return hasText(clientId) && hasText(clientSecret);
    }
}
