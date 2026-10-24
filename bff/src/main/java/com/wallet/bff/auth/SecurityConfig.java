package com.wallet.bff.auth;

import com.wallet.bff.config.CoreProperties;
import com.wallet.bff.config.JwtProperties;
import com.wallet.bff.config.WebProperties;
import com.wallet.bff.ratelimit.IpPseudonyms;
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
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.client.RestOperations;
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
    private static final String AUTH_ROUTES = "/api/auth/**";

    /** The page the e-mailed links open ({@code ConfirmationPageConfig}), with its script and style. */
    private static final String EMAIL_PAGES = "/email/**";

    /** The waitlist's routes, and the pages its e-mailed links open ({@code WaitlistPageConfig}). */
    private static final String WAITLIST_ROUTES = "/api/waitlist/**";
    private static final String WAITLIST_PAGES = "/waitlist/**";

    /** The page the "Esqueci minha senha" link opens ({@code PasswordResetPageConfig}). */
    private static final String PASSWORD_PAGES = "/password/**";

    /** Pluggy's webhooks: no session, a secret of their own (WebhookController). */
    private static final String WEBHOOK_ROUTES = "/api/webhooks/**";

    /**
     * For the pages of the e-mailed links, the only HTML the BFF serves, and harmless on the JSON routes:
     * nothing but its own script, style and calls, never inside a frame.
     */
    private static final String CONTENT_SECURITY_POLICY = "default-src 'none'; script-src 'self'; style-src 'self'; "
            + "connect-src 'self'; img-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonSecurityErrorHandler errorHandler,
                                            RateLimiter rateLimiter, RateLimitProperties rateLimits,
                                            IpPseudonyms ipPseudonyms) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(AUTH_ROUTES, EMAIL_PAGES, WAITLIST_ROUTES, WAITLIST_PAGES, PASSWORD_PAGES, WEBHOOK_ROUTES, "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(ignoringAuthRoutes())
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                // The token in a confirmation link sits after the #, which browsers never send; no
                // referrer either, so nothing of the page's address leaves it.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .addFilterAfter(new RateLimitFilter(rateLimiter, rateLimits, ipPseudonyms), BearerTokenAuthenticationFilter.class)
                .build();
    }

    /**
     * The auth routes do not look at the Bearer: the app refreshes exactly when its access token
     * has expired, and Spring would reject that stale header before the request reached refresh.
     */
    private static BearerTokenResolver ignoringAuthRoutes() {
        RequestMatcher authRoutes = PathPatternRequestMatcher.withDefaults().matcher(AUTH_ROUTES);
        RequestMatcher webhookRoutes = PathPatternRequestMatcher.withDefaults().matcher(WEBHOOK_ROUTES);
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        return request -> authRoutes.matches(request) || webhookRoutes.matches(request) ? null : resolver.resolve(request);
    }

    /**
     * Keys come from the core's JWKS, fetched on first use and cached: the BFF starts even when
     * the core is down, and a new key in the core is picked up without configuration. The fetch
     * carries the gateway key, like every other call to the core.
     */
    @Bean
    JwtDecoder jwtDecoder(CoreProperties core, JwtProperties jwt, RestOperations coreJwksRestOperations) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(core.jwksUri())
                .restOperations(coreJwksRestOperations)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(jwt.issuer()), new JwtAudienceValidator(jwt.audience())));
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
