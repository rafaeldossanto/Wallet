package com.wallet.bff.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;

/** Runs first, so even a 401 or a 429 goes out with its trace id. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** A client may send its own id, but never something that could forge log lines. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TraceContext.HEADER);
        if (isNull(traceId) || !SAFE_ID.matcher(traceId).matches()) {
            traceId = TraceContext.generate();
        }
        MDC.put(TraceContext.MDC_KEY, traceId);
        response.setHeader(TraceContext.HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceContext.MDC_KEY);
        }
    }
}
