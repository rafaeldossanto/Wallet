package com.wallet.bff.config;

import com.wallet.bff.ratelimit.ClientAddress;
import com.wallet.bff.ratelimit.RateLimitProperties;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

/**
 * Tells the core where the app's request came from, the same address the rate limit counts
 * ({@link ClientAddress}, trusted proxies included). The core scopes its sign-in lockout with it:
 * one address guessing a password locks itself out, not the account's owner everywhere else.
 *
 * <p>Only the BFF reaches the core (gateway key), so the core can take this header as the BFF's
 * word. Calls made off the request thread (the parts of a composed screen) go without it; none of
 * them checks a password.
 */
@Component
public class ClientAddressPropagationInterceptor implements ClientHttpRequestInterceptor {

    public static final String HEADER = "X-Wallet-Client-Address";

    private final ClientAddress clientAddress;

    public ClientAddressPropagationInterceptor(RateLimitProperties rateLimits) {
        this.clientAddress = new ClientAddress(rateLimits.trustedProxies());
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            request.getHeaders().set(HEADER, clientAddress.of(attributes.getRequest()));
        }
        return execution.execute(request, body);
    }
}
