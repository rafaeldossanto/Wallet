package com.wallet.bff.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Every request to the core carries the gateway key, the public routes and the JWKS included. */
@Component
@RequiredArgsConstructor
public class GatewayKeyInterceptor implements ClientHttpRequestInterceptor {

    private final GatewayKey gatewayKey;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().set(GatewayKey.HEADER, gatewayKey.value());
        return execution.execute(request, body);
    }
}
