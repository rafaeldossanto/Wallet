package com.wallet.bff.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class RestClientConfig {

    @Bean
    RestClient coreRestClient(RestClient.Builder builder, CoreProperties core,
                              BearerPropagationInterceptor bearer, TraceIdPropagationInterceptor trace) {
        // HTTP/1.1 on purpose: on plain http the JDK client tries an h2c upgrade, which the
        // internal network gains nothing from and test servers like WireMock reset.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(core.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(core.readTimeout());
        return builder.baseUrl(core.url())
                .requestFactory(requestFactory)
                .requestInterceptor(bearer)
                .requestInterceptor(trace)
                .build();
    }

    /**
     * Opens when the core stops answering (I/O errors and timeouts only). Business errors such as
     * 401 or 409 are answers, not failures: they never open the circuit.
     */
    @Bean
    CircuitBreaker coreCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(ResourceAccessException.class)
                .build();
        return CircuitBreaker.of("core", config);
    }
}
