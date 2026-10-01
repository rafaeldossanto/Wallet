package com.wallet.bff.auth;

import com.wallet.bff.config.CoreProperties;
import com.wallet.bff.config.JwtProperties;
import com.wallet.bff.config.WebProperties;
import com.wallet.bff.ratelimit.RateLimitFilter;
import com.wallet.bff.ratelimit.RateLimitProperties;
import com.wallet.bff.ratelimit.RateLimiter;
import com.wallet.bff.trace.TraceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * The public edge. Stateless with bearer tokens; the only cookie is the web refresh token, which
 * is limited to {@code /api/auth} and protected by the {@code X-Wallet-Client} header check in
 * {@code AuthController} (a cross-site form cannot set a custom header). That is why Spring's
 * CSRF tokens are off.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    public static final String CLIENT_HEADER = "X-Wallet-Client";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonSecurityErrorHandler errorHandler,
                                            RateLimiter rateLimiter, RateLimitProperties rateLimits) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .addFilterAfter(new RateLimitFilter(rateLimiter, rateLimits), BearerTokenAuthenticationFilter.class)
                .build();
    }

    /**
     * Keys come from the core's JWKS, fetched on first use and cached: the BFF starts even when
     * the core is down, and a new key in the core is picked up without configuration.
     */
    @Bean
    JwtDecoder jwtDecoder(CoreProperties core, JwtProperties jwt) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(core.jwksUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwt.issuer()));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(WebProperties web) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(web.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, CLIENT_HEADER,
                TraceContext.HEADER));
        cors.setExposedHeaders(List.of(TraceContext.HEADER));
        // The web app must send the refresh cookie to /api/auth/refresh.
        cors.setAllowCredentials(true);
        cors.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
