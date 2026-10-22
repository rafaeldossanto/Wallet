package com.wallet.core.connection.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** MEU_PLUGGY skips the check of whose item is linked: fine on a laptop, never on a server. */
class ConnectionConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConnectionConfig.class);

    @Test
    void withTheProdProfileMeuPluggyStopsTheBootNamingTheVariable() {
        runner.withPropertyValues("spring.profiles.active=prod", "wallet.provider.connection-mode=MEU_PLUGGY")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("WALLET_CONNECTION_MODE=PLUGGY_CONNECT"));
    }

    /** MEU_PLUGGY is the default, so a deploy that forgets the variable does not come up either. */
    @Test
    void withTheProdProfileTheDefaultModeStopsTheBootToo() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("WALLET_CONNECTION_MODE=PLUGGY_CONNECT"));
    }

    @Test
    void withTheProdProfilePluggyConnectStarts() {
        runner.withPropertyValues("spring.profiles.active=prod", "wallet.provider.connection-mode=PLUGGY_CONNECT")
                .run(context -> assertThat(context).hasNotFailed()
                        .getBean(ConnectionProperties.class).extracting(ConnectionProperties::isPluggyConnect).isEqualTo(true));
    }

    /** How the Item ID from the Meu Pluggy dashboard is linked while developing. */
    @Test
    void withoutTheProdProfileMeuPluggyStaysTheDefault() {
        runner.run(context -> assertThat(context).hasNotFailed()
                .getBean(ConnectionProperties.class).satisfies(properties -> {
                    assertThat(properties.connectionMode()).isEqualTo(ConnectionProperties.ConnectionMode.MEU_PLUGGY);
                    assertThat(properties.maxConnectionsPerUser()).isEqualTo(10);
                }));
    }
}
