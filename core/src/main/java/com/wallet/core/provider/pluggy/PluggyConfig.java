package com.wallet.core.provider.pluggy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PluggyProperties.class)
public class PluggyConfig {

    @Bean
    RestClient pluggyRestClient(RestClient.Builder builder, PluggyProperties properties) {
        return restClient(builder, properties);
    }

    /** Also used by the adapter test, which runs without a Spring context. */
    static RestClient restClient(RestClient.Builder builder, PluggyProperties properties) {
        // HTTP/1.1 on purpose: on plain http the JDK client tries an h2c upgrade that servers
        // like WireMock reset, and the Wallet's handful of calls gains nothing from multiplexing.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        return builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }
}
