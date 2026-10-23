package com.wallet.core.identity.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class ActiveUserWebConfig implements WebMvcConfigurer {

    private final ActiveUserInterceptor activeUsers;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(activeUsers);
    }
}
