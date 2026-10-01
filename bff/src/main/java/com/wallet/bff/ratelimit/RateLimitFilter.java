package com.wallet.bff.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs inside the security chain, right after the token is read, so it can count per user.
 * Login and the other {@code /api/auth} routes count per IP with a lower limit: that is where
 * brute force goes, and every attempt costs a BCrypt in the core.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!properties.enabled() || path.startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }
        boolean authRoute = path.startsWith("/api/auth/");
        String key = authRoute ? "auth:" + request.getRemoteAddr() : "api:" + caller(request);
        int limit = authRoute ? properties.authLimit() : properties.defaultLimit();
        if (!rateLimiter.tryAcquire(key, limit)) {
            log.warn("[RATE-LIMIT] {} went over {} requests per {}", key, limit, properties.window());
            reject(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static String caller(HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            return "user:" + token.getToken().getSubject();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(properties.window().toSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Fixed text, nothing from the request in it: nothing to escape.
        response.getWriter().write("{\"code\":\"rate_limit.exceeded\",\"message\":\"Too many requests; try again shortly\"}");
    }
}
