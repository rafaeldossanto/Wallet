package com.wallet.bff.client;

import com.wallet.bff.exception.CoreErrorException;
import com.wallet.bff.exception.CoreUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * HTTP to the core. Typed calls per route live in {@link CoreApi}; this class only knows how to
 * send, how to turn the core's errors into {@link CoreErrorException} and when the core is down.
 */
@Component
public class CoreClient {

    /** Any 4xx or 5xx from the core travels on with its status and body. */
    private static final RestClient.ResponseSpec.ErrorHandler RELAY_ERRORS = (request, response) -> {
        throw new CoreErrorException(response.getStatusCode().value(), response.getBody().readAllBytes());
    };

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public CoreClient(@Qualifier("coreRestClient") RestClient restClient, CircuitBreaker coreCircuitBreaker) {
        this.restClient = restClient;
        this.circuitBreaker = coreCircuitBreaker;
    }

    public <T> T get(String path, Map<String, Object> query, ParameterizedTypeReference<T> type) {
        return call(() -> restClient.get()
                .uri(builder -> withQuery(builder.path(path), query))
                .retrieve()
                .onStatus(HttpStatusCode::isError, RELAY_ERRORS)
                .body(type));
    }

    public <T> T post(String path, Object body, ParameterizedTypeReference<T> type) {
        return call(() -> postSpec(path, body).body(type));
    }

    public void post(String path, Object body) {
        call(() -> postSpec(path, body).toBodilessEntity());
    }

    public void delete(String path) {
        call(() -> restClient.delete()
                .uri(path)
                .retrieve()
                .onStatus(HttpStatusCode::isError, RELAY_ERRORS)
                .toBodilessEntity());
    }

    private RestClient.ResponseSpec postSpec(String path, Object body) {
        RestClient.RequestBodySpec request = restClient.post().uri(path).contentType(MediaType.APPLICATION_JSON);
        if (nonNull(body)) {
            request.body(body);
        }
        return request.retrieve().onStatus(HttpStatusCode::isError, RELAY_ERRORS);
    }

    private <T> T call(Supplier<T> request) {
        try {
            return circuitBreaker.executeSupplier(request);
        } catch (CallNotPermittedException ex) {
            throw new CoreUnavailableException("Circuit to the core is open", ex);
        } catch (ResourceAccessException ex) {
            throw new CoreUnavailableException("Core did not answer: " + ex.getMessage(), ex);
        }
    }

    /** Values go through URI variables, encoded strictly: a search term may contain {@code &} or {@code +}. */
    private static URI withQuery(UriBuilder builder, Map<String, Object> query) {
        if (isNull(query) || query.isEmpty()) {
            return builder.build();
        }
        query.keySet().forEach(name -> builder.queryParam(name, "{" + name + "}"));
        return builder.build(query);
    }
}
