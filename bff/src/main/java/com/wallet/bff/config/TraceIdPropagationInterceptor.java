package com.wallet.bff.config;

import com.wallet.bff.trace.TraceContext;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static java.util.Objects.nonNull;

@Component
public class TraceIdPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String traceId = TraceContext.current();
        if (nonNull(traceId)) {
            request.getHeaders().set(TraceContext.HEADER, traceId);
        }
        return execution.execute(request, body);
    }
}
