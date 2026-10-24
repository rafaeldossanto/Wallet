package com.wallet.core.waitlist.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WaitlistProperties.class)
public class WaitlistConfig {
}
