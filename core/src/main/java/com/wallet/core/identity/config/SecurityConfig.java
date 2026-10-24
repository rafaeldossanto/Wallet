package com.wallet.core.identity.config;

import com.wallet.core.shared.security.GatewayKey;
import com.wallet.core.shared.security.GatewayKeyFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The core is only reached by the BFF. It is stateless and token-based: cookies, CORS and CSRF
 * are handled by the BFF, which is the public edge. Being on an internal network is not taken for
 * granted: every request must also carry the gateway key ({@link GatewayKeyFilter}), the public
 * routes below included.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({JwtProperties.class, AuthProperties.class, IdentityRetentionProperties.class})
public class SecurityConfig {

    /** Only a web application has a filter chain: the announcement command runs without one. */
    @Bean
    @ConditionalOnWebApplication
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonSecurityErrorHandler errorHandler,
                                            GatewayKey gatewayKey) throws Exception {
        return http
                .addFilterBefore(new GatewayKeyFilter(gatewayKey), BearerTokenAuthenticationFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/internal/auth/**", "/internal/waitlist/**", "/internal/webhooks/**", "/internal/.well-known/jwks.json", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .build();
    }

    /**
     * BCrypt today, behind the delegating encoder so the algorithm can change later without
     * invalidating the hashes already stored ({@code {bcrypt}...} prefix).
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
