package com.wallet.core.connection.config;

import com.wallet.core.connection.config.ConnectionProperties.ConnectionMode;
import com.wallet.core.shared.security.ProductionProfile;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * MEU_PLUGGY accepts items that carry no owner, from any user: that is how the Item ID from the
 * Meu Pluggy dashboard gets linked while developing. On a server it would let whoever learns an
 * item id of the operator read the operator's bank data, so with the {@code prod} profile the core
 * does not start in that mode. It does not switch to PLUGGY_CONNECT on its own either: a deploy
 * says which mode it runs in.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConnectionProperties.class)
public class ConnectionConfig {

    ConnectionConfig(ConnectionProperties properties, Environment environment) {
        requireAllowedMode(properties.connectionMode(), ProductionProfile.isActive(environment));
    }

    static void requireAllowedMode(ConnectionMode mode, boolean production) {
        if (production && !ConnectionMode.PLUGGY_CONNECT.equals(mode)) {
            throw new IllegalStateException("wallet.provider.connection-mode " + mode + " is for development only: "
                    + "it skips the check of whose item is being linked. Set WALLET_CONNECTION_MODE=PLUGGY_CONNECT "
                    + "with the prod profile");
        }
    }
}
