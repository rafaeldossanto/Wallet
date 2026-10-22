package com.wallet.core.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Refuses every request without the gateway key before anything else looks at it: no token
 * parsing, no BCrypt, no database. The health checks never get here: they have a chain of their
 * own ({@link ManagementSecurityConfig}), and the only one open on this port, {@code /livez},
 * reads nothing.
 *
 * <p>Answers 403, not 401: the BFF relays the core's 401 as "session expired" and the app would
 * log the user out over what is a server misconfiguration.
 *
 * <p>Not a bean on purpose: Spring Boot would also register it as a servlet filter of its own. It
 * runs inside the security chain ({@code SecurityConfig}).
 */
@Slf4j
public class GatewayKeyFilter extends OncePerRequestFilter {

    private final GatewayKey gatewayKey;

    public GatewayKeyFilter(GatewayKey gatewayKey) {
        this.gatewayKey = gatewayKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (gatewayKey.matches(request.getHeader(GatewayKey.HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("[SECURITY] {} {} refused: no valid gateway key", request.getMethod(), request.getRequestURI());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Fixed text, nothing from the request in it: nothing to escape.
        response.getWriter().write("{\"code\":\"gateway.forbidden\",\"message\":\"Only the BFF may call the core\"}");
    }
}
