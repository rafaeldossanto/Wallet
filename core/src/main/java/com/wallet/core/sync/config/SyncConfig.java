package com.wallet.core.sync.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Async runs on Spring Boot's applicationTaskExecutor, which uses virtual threads here. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(SyncProperties.class)
public class SyncConfig {
}
