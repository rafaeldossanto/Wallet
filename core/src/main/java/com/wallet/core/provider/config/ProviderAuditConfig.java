package com.wallet.core.provider.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Scheduling is enabled here too, so the nightly cleanup does not depend on the sync module. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(ProviderAuditProperties.class)
public class ProviderAuditConfig {
}
