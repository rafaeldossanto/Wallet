package com.wallet.bff.service;

import com.wallet.bff.cache.ScreenCache;
import com.wallet.bff.client.CoreApi;
import com.wallet.bff.config.RequestContextExecutor;
import com.wallet.bff.exception.CoreErrorException;
import com.wallet.bff.model.dto.request.ExpectedSalaryRequest;
import com.wallet.bff.model.dto.request.FixedExpenseRequest;
import com.wallet.bff.model.dto.response.ConnectionResponse;
import com.wallet.bff.model.dto.response.ExpectedSalaryResponse;
import com.wallet.bff.model.dto.response.FixedExpenseResponse;
import com.wallet.bff.model.dto.response.PlanningResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * The planning screen: the core's plan for the month with each card's bank. Cached per user and
 * month like the other screens; changing a fixed expense or the expected salary drops it, and so
 * does a sync that lands (the connections are read here too, which is how the BFF notices).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanningService {

    static final String SCREEN = "planning";
    private static final String SYNCING = "SYNCING";

    private final CoreApi coreApi;
    private final RequestContextExecutor executor;
    private final ScreenCache cache;

    /** {@code month=2026-10}; the core validates it and defaults to the current month. */
    public PlanningResponse month(UUID userId, String month) {
        return cache.get(userId, SCREEN, isNull(month) ? "" : month, () -> compose(userId, month), Composed::cacheable)
                .plan();
    }

    public List<FixedExpenseResponse> fixedExpenses() {
        return coreApi.fixedExpenses();
    }

    public FixedExpenseResponse createFixedExpense(UUID userId, FixedExpenseRequest request) {
        FixedExpenseResponse created = coreApi.createFixedExpense(request);
        cache.evictScreen(userId, SCREEN);
        return created;
    }

    public FixedExpenseResponse updateFixedExpense(UUID userId, UUID id, FixedExpenseRequest request) {
        FixedExpenseResponse updated = coreApi.updateFixedExpense(id, request);
        cache.evictScreen(userId, SCREEN);
        return updated;
    }

    public void deleteFixedExpense(UUID userId, UUID id) {
        coreApi.deleteFixedExpense(id);
        cache.evictScreen(userId, SCREEN);
    }

    public ExpectedSalaryResponse setExpectedSalary(UUID userId, ExpectedSalaryRequest request) {
        ExpectedSalaryResponse saved = coreApi.setExpectedSalary(request);
        cache.evictScreen(userId, SCREEN);
        return saved;
    }

    public void clearExpectedSalary(UUID userId) {
        coreApi.clearExpectedSalary();
        cache.evictScreen(userId, SCREEN);
    }

    /** Without the names the plan still comes back, but is not kept: the next look tries again. */
    private Composed compose(UUID userId, String month) {
        CompletableFuture<PlanningResponse> plan = executor.supply(() -> coreApi.planning(month));
        CompletableFuture<List<ConnectionResponse>> connections = executor.supply(coreApi::connections);

        PlanningResponse result = join(plan);
        List<ConnectionResponse> named = optional(connections);
        if (isNull(named)) {
            return new Composed(result.withInstitutions(Map.of()), false);
        }
        cache.noteSyncState(userId, SyncStates.of(named));
        Map<UUID, ConnectionResponse> byId = named.stream()
                .collect(Collectors.toMap(ConnectionResponse::id, Function.identity(), (first, second) -> first));
        return new Composed(result.withInstitutions(byId), settled(named));
    }

    /** A plan composed while a connection is still bringing data would be stale for the cache's whole TTL. */
    private static boolean settled(List<ConnectionResponse> connections) {
        return connections.stream()
                .allMatch(connection -> nonNull(connection.lastSyncedAt()) && !SYNCING.equals(connection.status()));
    }

    /** The names only dress the bills: when they fail, they are left out. A 401 still ends the call. */
    private static List<ConnectionResponse> optional(CompletableFuture<List<ConnectionResponse>> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof CoreErrorException coreError && coreError.isUnauthorized()) {
                throw coreError;
            }
            log.warn("[PLANNING] connections unavailable, names left out: {}", ex.getCause().getMessage());
            return null;
        }
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            throw ex.getCause() instanceof RuntimeException runtime ? runtime : ex;
        }
    }

    private record Composed(PlanningResponse plan, boolean cacheable) {
    }
}
