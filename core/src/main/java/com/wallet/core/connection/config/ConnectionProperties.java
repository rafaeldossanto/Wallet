package com.wallet.core.connection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param connectionMode   MEU_PLUGGY while developing (items come from the Meu Pluggy dashboard),
 *                         PLUGGY_CONNECT in production (items come from the widget, per user)
 * @param oauthRedirectUri where the bank sends the user back after authorizing in the Pluggy
 *                         Connect widget: the Android app's deep link (its intent filter in
 *                         {@code app/android/app/src/main/AndroidManifest.xml}). Pluggy accepts
 *                         HTTPS or a deep link, never localhost.
 */
@ConfigurationProperties("wallet.provider")
public record ConnectionProperties(
        @DefaultValue("MEU_PLUGGY") ConnectionMode connectionMode,
        @DefaultValue("com.wallet.wallet://pluggy-connect") String oauthRedirectUri) {

    public enum ConnectionMode {
        MEU_PLUGGY,
        PLUGGY_CONNECT
    }

    public boolean isPluggyConnect() {
        return ConnectionMode.PLUGGY_CONNECT.equals(connectionMode);
    }
}
