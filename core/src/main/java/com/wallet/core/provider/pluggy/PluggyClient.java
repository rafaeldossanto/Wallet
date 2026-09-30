package com.wallet.core.provider.pluggy;

import com.wallet.core.provider.ProviderErrors;
import com.wallet.core.shared.error.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * HTTP access to Pluggy. Handles the apiKey, pagination and error translation; knows nothing
 * about the Wallet's model ({@link PluggyMapper} does the conversion).
 */
@Slf4j
@Component
class PluggyClient {

    static final String API_KEY_HEADER = "X-API-KEY";
    private static final Duration RENEWAL_MARGIN = Duration.ofMinutes(10);
    private static final int PAGE_SIZE = 500;

    private final RestClient restClient;
    private final PluggyProperties properties;
    private final Clock clock;
    private final ReentrantLock authLock = new ReentrantLock();
    private volatile CachedApiKey cachedApiKey;

    PluggyClient(@Qualifier("pluggyRestClient") RestClient restClient, PluggyProperties properties, Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    PluggyDtos.Item getItem(String itemId) {
        return withApiKey(apiKey -> restClient.get()
                .uri(builder -> builder.path("/items/{id}").build(itemId))
                .header(API_KEY_HEADER, apiKey)
                .retrieve()
                .body(PluggyDtos.Item.class));
    }

    void deleteItem(String itemId) {
        withApiKey(apiKey -> restClient.delete()
                .uri(builder -> builder.path("/items/{id}").build(itemId))
                .header(API_KEY_HEADER, apiKey)
                .retrieve()
                .toBodilessEntity());
    }

    List<PluggyDtos.Account> getAccounts(String itemId) {
        return getAllPages("/accounts", Map.of("itemId", itemId), new ParameterizedTypeReference<>() {});
    }

    List<PluggyDtos.Bill> getBills(String accountId) {
        return getAllPages("/bills", Map.of("accountId", accountId), new ParameterizedTypeReference<>() {});
    }

    List<PluggyDtos.Investment> getInvestments(String itemId) {
        return getAllPages("/investments", Map.of("itemId", itemId), new ParameterizedTypeReference<>() {});
    }

    /**
     * Uses {@code /v2/transactions} (cursor pagination). The page-based {@code /transactions} is
     * deprecated in Pluggy's SDK: offsets shift when transactions are inserted during the sweep.
     */
    List<PluggyDtos.Transaction> getTransactions(String accountId, LocalDate from, LocalDate to) {
        List<PluggyDtos.Transaction> transactions = new ArrayList<>();
        String after = null;
        do {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("accountId", accountId);
            query.put("dateFrom", from);
            query.put("dateTo", to);
            if (nonNull(after)) {
                query.put("after", after);
            }
            PluggyDtos.CursorPage<PluggyDtos.Transaction> page =
                    get("/v2/transactions", query, new ParameterizedTypeReference<>() {});
            transactions.addAll(page.results());
            after = afterCursorOf(page.next());
        } while (nonNull(after));
        return transactions;
    }

    private <T> List<T> getAllPages(String path, Map<String, Object> filters,
                                    ParameterizedTypeReference<PluggyDtos.Page<T>> type) {
        List<T> results = new ArrayList<>();
        int page = 1;
        int totalPages;
        do {
            Map<String, Object> query = new LinkedHashMap<>(filters);
            query.put("page", page);
            query.put("pageSize", PAGE_SIZE);
            PluggyDtos.Page<T> response = get(path, query, type);
            results.addAll(response.results());
            totalPages = isNull(response.totalPages()) ? 1 : response.totalPages();
            page++;
        } while (page <= totalPages);
        return results;
    }

    private <T> T get(String path, Map<String, Object> query, ParameterizedTypeReference<T> type) {
        return withApiKey(apiKey -> restClient.get()
                .uri(builder -> withQuery(builder.path(path), query))
                .header(API_KEY_HEADER, apiKey)
                .retrieve()
                .body(type));
    }

    /**
     * Values go through URI variables, which are encoded strictly. A base64 cursor has {@code +}
     * and {@code /}; sent as a literal, the {@code +} would arrive at Pluggy as a space.
     */
    private static URI withQuery(UriBuilder builder, Map<String, Object> query) {
        query.keySet().forEach(name -> builder.queryParam(name, "{" + name + "}"));
        return builder.build(query);
    }

    private static String afterCursorOf(String next) {
        if (isNull(next)) {
            return null;
        }
        String raw = UriComponentsBuilder.fromUriString(next).build().getQueryParams().getFirst("after");
        return isNull(raw) ? null : URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    /** One retry with a fresh apiKey when Pluggy answers 401: the key may have been revoked early. */
    private <T> T withApiKey(Function<String, T> call) {
        String apiKey = apiKey();
        try {
            return call.apply(apiKey);
        } catch (HttpClientErrorException.Unauthorized ex) {
            forget(apiKey);
            return translating(call, apiKey());
        } catch (RestClientException ex) {
            throw translate(ex);
        }
    }

    private <T> T translating(Function<String, T> call, String apiKey) {
        try {
            return call.apply(apiKey);
        } catch (RestClientException ex) {
            throw translate(ex);
        }
    }

    private String apiKey() {
        CachedApiKey cached = cachedApiKey;
        if (nonNull(cached) && cached.isValidAt(clock.instant())) {
            return cached.value();
        }
        authLock.lock();
        try {
            cached = cachedApiKey;
            if (nonNull(cached) && cached.isValidAt(clock.instant())) {
                return cached.value();
            }
            String apiKey = authenticate();
            cachedApiKey = new CachedApiKey(apiKey, clock.instant().plus(properties.apiKeyTtl()).minus(RENEWAL_MARGIN));
            return apiKey;
        } finally {
            authLock.unlock();
        }
    }

    private String authenticate() {
        if (!properties.hasCredentials()) {
            throw ProviderErrors.notConfigured();
        }
        try {
            PluggyDtos.AuthResponse response = restClient.post()
                    .uri("/auth")
                    .body(new PluggyDtos.AuthRequest(properties.clientId(), properties.clientSecret(), false))
                    .retrieve()
                    .body(PluggyDtos.AuthResponse.class);
            if (isNull(response) || isNull(response.apiKey())) {
                throw ProviderErrors.unavailable("empty auth response");
            }
            return response.apiKey();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
            throw ProviderErrors.authFailed();
        } catch (RestClientException ex) {
            throw translate(ex);
        }
    }

    private void forget(String rejectedApiKey) {
        CachedApiKey cached = cachedApiKey;
        if (nonNull(cached) && cached.value().equals(rejectedApiKey)) {
            cachedApiKey = null;
        }
    }

    private static DomainException translate(RestClientException ex) {
        if (ex instanceof HttpClientErrorException.NotFound) {
            return ProviderErrors.notFound("such resource");
        }
        if (ex instanceof HttpClientErrorException.Unauthorized || ex instanceof HttpClientErrorException.Forbidden) {
            return ProviderErrors.authFailed();
        }
        log.warn("[PLUGGY] Request failed: {}", ex.getMessage());
        return ProviderErrors.unavailable(ex.getClass().getSimpleName());
    }

    private record CachedApiKey(String value, Instant validUntil) {

        boolean isValidAt(Instant now) {
            return validUntil.isAfter(now);
        }
    }
}
