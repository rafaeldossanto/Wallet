package com.wallet.bff.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static java.util.Objects.nonNull;

/**
 * Runs the parts of a composed screen in parallel on virtual threads, carrying the request's
 * security context and MDC: without them the Bearer and the trace id would not reach the core.
 */
@Component
public class RequestContextExecutor implements DisposableBean {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public <T> CompletableFuture<T> supply(Supplier<T> task) {
        SecurityContext security = SecurityContextHolder.getContext();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return CompletableFuture.supplyAsync(() -> {
            SecurityContextHolder.setContext(security);
            if (nonNull(mdc)) {
                MDC.setContextMap(mdc);
            }
            try {
                return task.get();
            } finally {
                SecurityContextHolder.clearContext();
                MDC.clear();
            }
        }, executor);
    }

    @Override
    public void destroy() {
        executor.close();
    }
}
