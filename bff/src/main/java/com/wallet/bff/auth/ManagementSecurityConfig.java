package com.wallet.bff.auth;

import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/**
 * The health checks, ahead of the main chain: no token, no rate limit. Two kinds of request end
 * up here:
 *
 * <ul>
 *   <li>{@code /livez} on the public port: liveness only, answered from memory, so a load
 *       balancer can poll it without a token and a flood of it costs less than the rate limiter's
 *       own bookkeeping would;</li>
 *   <li>anything on the management port ({@code /actuator/health} with its details, the readiness
 *       probe): that port listens on loopback only, so whoever reaches it is already on the
 *       machine.</li>
 * </ul>
 *
 * <p>Both rely on the management port being a port of its own: on the public port the details
 * would be open to the internet. The boot refuses that setup.
 */
@Configuration(proxyBeanMethods = false)
public class ManagementSecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain managementSecurityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        if (ManagementPortType.SAME.equals(ManagementPortType.get(environment))) {
            throw new IllegalStateException("WALLET_BFF_MANAGEMENT_PORT (management.server.port) must differ from "
                    + "the BFF's port: the health details and the readiness probe must not be public");
        }
        return http
                .securityMatcher(new OrRequestMatcher(
                        EndpointRequest.toAnyEndpoint(),
                        EndpointRequest.toAdditionalPaths(WebServerNamespace.SERVER, HealthEndpoint.class)))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
