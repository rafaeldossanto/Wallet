package com.wallet.bff.service;

import com.wallet.bff.cache.ScreenCache;
import com.wallet.bff.client.CoreApi;
import com.wallet.bff.config.ClockConfig;
import com.wallet.bff.config.RequestContextExecutor;
import com.wallet.bff.exception.CoreErrorException;
import com.wallet.bff.model.dto.response.AccountResponse;
import com.wallet.bff.model.dto.response.ConnectionResponse;
import com.wallet.bff.model.dto.response.CreditCardResponse;
import com.wallet.bff.model.dto.response.HomeResponse;
import com.wallet.bff.model.dto.response.OverviewResponse;
import com.wallet.bff.model.dto.response.PageResponse;
import com.wallet.bff.model.dto.response.TransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

/**
 * The home screen: five calls to the core in parallel, one answer to the app. A failed part leaves
 * a hole and a name in {@code unavailable}; an expired session (401) fails the whole screen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeService {

    static final String SCREEN = "home";
    private static final int RECENT_TRANSACTIONS = 5;
    private static final int RECENT_DAYS = 30;

    private final CoreApi coreApi;
    private final RequestContextExecutor executor;
    private final ScreenCache cache;
    private final Clock clock;

    public HomeResponse home(UUID userId) {
        return cache.get(userId, SCREEN, "", this::compose, HomeResponse::isComplete);
    }

    private HomeResponse compose() {
        LocalDate today = ClockConfig.today(clock);
        CompletableFuture<OverviewResponse> overview = executor.supply(coreApi::overview);
        CompletableFuture<List<AccountResponse>> accounts = executor.supply(coreApi::accounts);
        CompletableFuture<List<ConnectionResponse>> connections = executor.supply(coreApi::connections);
        CompletableFuture<List<CreditCardResponse>> cards = executor.supply(coreApi::creditCards);
        CompletableFuture<PageResponse<TransactionResponse>> recent = executor.supply(() -> coreApi.transactions(Map.of(
                "from", today.minusDays(RECENT_DAYS - 1).toString(),
                "to", today.toString(),
                "pageSize", String.valueOf(RECENT_TRANSACTIONS))));

        List<String> unavailable = new ArrayList<>();
        List<RuntimeException> failures = new ArrayList<>();
        OverviewResponse overviewPart = part("overview", overview, unavailable, failures);
        List<AccountResponse> accountsPart = part("accounts", accounts, unavailable, failures);
        List<ConnectionResponse> connectionsPart = part("connections", connections, unavailable, failures);
        List<CreditCardResponse> cardsPart = part("creditCards", cards, unavailable, failures);
        PageResponse<TransactionResponse> recentPart = part("recentTransactions", recent, unavailable, failures);

        if (failures.size() == 5) {
            throw failures.getFirst();
        }
        return new HomeResponse(
                overviewPart,
                institutions(connectionsPart, accountsPart),
                isNull(cardsPart) ? List.of() : cardsPart,
                isNull(recentPart) ? List.of() : recentPart.items(),
                List.copyOf(unavailable));
    }

    /** Waits for one part. 401 means the session is gone: no point showing half a screen. */
    private static <T> T part(String name, CompletableFuture<T> future, List<String> unavailable,
                              List<RuntimeException> failures) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            RuntimeException cause = ex.getCause() instanceof RuntimeException runtime ? runtime : ex;
            if (cause instanceof CoreErrorException coreError && coreError.isUnauthorized()) {
                throw coreError;
            }
            log.warn("[HOME] Part {} unavailable: {}", name, cause.getMessage());
            unavailable.add(name);
            failures.add(cause);
            return null;
        }
    }

    /**
     * Bank accounts under their institution, in the order the user linked them. Credit cards have
     * their own block. When the connections did not load, accounts are still grouped by id.
     */
    private static List<HomeResponse.Institution> institutions(List<ConnectionResponse> connections,
                                                                List<AccountResponse> accounts) {
        Map<UUID, List<AccountResponse>> byConnection = isNull(accounts) ? Map.of() : accounts.stream()
                .filter(account -> !account.isCreditCard())
                .collect(Collectors.groupingBy(AccountResponse::connectionId, LinkedHashMap::new, Collectors.toList()));
        if (isNull(connections)) {
            return byConnection.entrySet().stream()
                    .map(entry -> new HomeResponse.Institution(entry.getKey(), null, null, null, null, entry.getValue()))
                    .toList();
        }
        return connections.stream()
                .map(connection -> new HomeResponse.Institution(connection.id(), connection.institutionName(),
                        connection.institutionImageUrl(), connection.status(), connection.lastSyncedAt(),
                        byConnection.getOrDefault(connection.id(), List.of())))
                .toList();
    }
}
