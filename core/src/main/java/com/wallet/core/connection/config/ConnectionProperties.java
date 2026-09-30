package com.wallet.core.connection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param connectionMode MEU_PLUGGY while developing (items come from the Meu Pluggy dashboard),
 *                       PLUGGY_CONNECT in production (items come from the widget, per user)
 */
@ConfigurationProperties("wallet.provider")
public record ConnectionProperties(@DefaultValue("MEU_PLUGGY") ConnectionMode connectionMode) {

    public enum ConnectionMode {
        MEU_PLUGGY,
        PLUGGY_CONNECT
    }

    public boolean isPluggyConnect() {
        return ConnectionMode.PLUGGY_CONNECT.equals(connectionMode);
    }
}
