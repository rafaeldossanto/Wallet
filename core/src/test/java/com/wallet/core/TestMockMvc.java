package com.wallet.core;

import com.wallet.core.shared.security.GatewayKey;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.util.StringUtils.hasText;

/**
 * MockMvc that calls the core the way the BFF does: with the gateway key on every request.
 *
 * <p>Built by hand with {@code springSecurity()}: in Spring Boot 4, {@code @AutoConfigureMockMvc}
 * no longer applies the security filter chain.
 */
public final class TestMockMvc {

    private TestMockMvc() {
    }

    public static MockMvc of(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").header(GatewayKey.HEADER, gatewayKey(context)))
                .build();
    }

    /** Without the key, as anyone who reaches the core's port. */
    public static MockMvc withoutGatewayKey(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** The configured key, or the development one the core falls back to. */
    public static String gatewayKey(WebApplicationContext context) {
        String configured = context.getEnvironment().getProperty("wallet.gateway.key");
        return hasText(configured) ? configured.strip() : GatewayKey.DEVELOPMENT_KEY;
    }
}
