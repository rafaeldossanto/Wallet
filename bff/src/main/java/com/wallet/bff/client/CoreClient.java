package com.wallet.bff.client;

import com.wallet.bff.exception.CoreErrorException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.Map;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * HTTP to the core. Typed calls per route live in {@link CoreApi}; this class only knows how to
 * send, how to turn the core's errors into {@link CoreErrorException} and, through
 * {@link CoreCallGuards}, when a group of routes is down or full. Every call names its
 * {@link CoreRouteGroup}.
 */
@Component
public class CoreClient {

    /** Any 4xx or 5xx from the core travels on with its status and body. */
    private static final RestClient.ResponseSpec.ErrorHandler RELAY_ERRORS = (request, response) -> {
        throw new CoreErrorException(response.getStatusCode().value(), response.getBody().readAllBytes());
    };

    private final RestClient restClient;
    private final RestClient slowRestClient;
    private final CoreCallGuards guards;

    public CoreClient(@Qualifier("coreRestClient") RestClient restClient,
                      @Qualifier("coreSlowRestClient") RestClient slowRestClient, CoreCallGuards guards) {
        this.restClient = restClient;
        this.slowRestClient = slowRestClient;
        this.guards = guards;
    }

    public <T> T get(CoreRouteGroup group, String path, Map<String, Object> query, ParameterizedTypeReference<T> type) {
        return guards.call(group, () -> restClient.get()
                .uri(builder -> withQuery(builder.path(path), query))
                .retrieve()
                .onStatus(HttpStatusCode::isError, RELAY_ERRORS)
                .body(type));
    }

    public <T> T post(CoreRouteGroup group, String path, Object body, ParameterizedTypeReference<T> type) {
        return guards.call(group, () -> postSpec(path, body).body(type));
    }

    public void post(CoreRouteGroup group, String path, Object body) {
        guards.call(group, () -> postSpec(path, body).toBodilessEntity());
    }

    public <T> T put(CoreRouteGroup group, String path, Object body, ParameterizedTypeReference<T> type) {
        return guards.call(group, () -> restClient.put()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, RELAY_ERRORS)
                .body(type));
    }

    /** @param uriVariables fill {@code {name}} placeholders in {@code path}, encoded strictly: a client's id may hold a {@code /} */
    public void delete(CoreRouteGroup group, String path, Object... uriVariables) {
        guards.call(group, () -> restClient.delete()
                .uri(path, uriVariables)
                .retrieve()
                .onStatus(HttpStatusCode::isError, RELAY_ERRORS)
                .toBodilessEntity());
    }

    /** A DELETE with a body (the password again, for one). */
    public void deleteWithBody(CoreRouteGroup group, String path, Object body) {
        guards.call(group, () -> restClient.method(HttpMethod.DELETE)
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, RELAY_ERRORS)
                .toBodilessEntity());
    }

    /** A DELETE with a body, for a route where the core waits on the provider: the longer read timeout. */
    public void deleteWaitingOnProvider(String path, Object body) {
        guards.call(CoreRouteGroup.PROVIDER, () -> slowRestClient.method(HttpMethod.DELETE)
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
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

    /** Values go through URI variables, encoded strictly: a search term may contain {@code &} or {@code +}. */
    private static URI withQuery(UriBuilder builder, Map<String, Object> query) {
        if (isNull(query) || query.isEmpty()) {
            return builder.build();
        }
        query.keySet().forEach(name -> builder.queryParam(name, "{" + name + "}"));
        return builder.build(query);
    }
}
