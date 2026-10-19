package com.wallet.bff.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class RestClientConfig {

    @Bean
    RestClient coreRestClient(RestClient.Builder builder, CoreProperties core, GatewayKeyInterceptor gatewayKey,
                              BearerPropagationInterceptor bearer, TraceIdPropagationInterceptor trace) {
        return builder.baseUrl(core.url())
                .requestFactory(coreRequestFactory(core))
                .requestInterceptor(gatewayKey)
                .requestInterceptor(bearer)
                .requestInterceptor(trace)
                .build();
    }

    /**
     * Fetches the core's JWKS for the token decoder. The core asks the gateway key of this route
     * too, and Spring Security's decoder only takes a {@link RestOperations}.
     */
    @Bean
    RestOperations coreJwksRestOperations(CoreProperties core, GatewayKeyInterceptor gatewayKey) {
        RestTemplate restTemplate = new RestTemplate(coreRequestFactory(core));
        restTemplate.getInterceptors().add(gatewayKey);
        return restTemplate;
    }

    /**
     * HTTP/1.1 on purpose: on plain http the JDK client tries an h2c upgrade, which the internal
     * network gains nothing from and test servers like WireMock reset.
     */
    private static JdkClientHttpRequestFactory coreRequestFactory(CoreProperties core) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(core.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(core.readTimeout());
        return requestFactory;
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
