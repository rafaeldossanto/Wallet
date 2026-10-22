package com.wallet.core;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** E-mails go to a {@link RecordingMailTransport} the test reads, links included. */
@TestConfiguration(proxyBeanMethods = false)
public class TestMailConfiguration {

    @Bean
    @Primary
    RecordingMailTransport recordingMailTransport() {
        return new RecordingMailTransport();
    }
}
