package com.wallet.bff.service;

import com.wallet.bff.cache.ScreenCache;
import com.wallet.bff.client.CoreApi;
import com.wallet.bff.config.RequestContextExecutor;
import com.wallet.bff.exception.CoreErrorException;
import com.wallet.bff.model.dto.response.AccountResponse;
import com.wallet.bff.model.dto.response.CalendarResponse;
import com.wallet.bff.model.dto.response.ConnectionResponse;
import com.wallet.bff.model.dto.response.PageResponse;
import com.wallet.bff.model.dto.response.SpendingListResponse;
import com.wallet.bff.model.dto.response.TransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

/**
 * The spending calendar on the home screen: which days had spending, and what was spent (on the
 * picked day, or over the whole month when no day is picked) and at which bank.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarService {

    public static final int PAGE_SIZE = 50;

    private final CoreApi coreApi;
    private final RequestContextExecutor executor;
    private final ScreenCache cache;

    public CalendarResponse month(UUID userId, String month) {
        return cache.get(userId, "calendar", isNull(month) ? "" : month, () -> coreApi.dailySpending(month));
    }

    /**
     * The period's spending (same rule as the calendar, so it adds up to the calendar's totals),
     * newest first, with the account and bank names. Without the names the list still comes back;
     * without the list there is nothing to show.
     */
    public SpendingListResponse spending(LocalDate from, LocalDate to, int page) {
        CompletableFuture<PageResponse<TransactionResponse>> spending = executor.supply(() -> coreApi.transactions(Map.of(
                "from", from.toString(),
                "to", to.toString(),
                "spending", "true",
                "page", String.valueOf(page),
                "pageSize", String.valueOf(PAGE_SIZE))));
        CompletableFuture<List<AccountResponse>> accounts = executor.supply(coreApi::accounts);
        CompletableFuture<List<ConnectionResponse>> connections = executor.supply(coreApi::connections);

        PageResponse<TransactionResponse> result = join(spending);
        Map<UUID, AccountResponse> accountsById = byId(optional("accounts", accounts), AccountResponse::id);
        Map<UUID, ConnectionResponse> connectionsById = byId(optional("connections", connections), ConnectionResponse::id);

        return new SpendingListResponse(from, to, result.page(), result.totalPages(), result.total(), result.items().stream()
                .map(transaction -> item(transaction, accountsById.get(transaction.accountId()), connectionsById))
                .toList());
    }

    private static SpendingListResponse.Item item(TransactionResponse transaction, AccountResponse account,
                                                  Map<UUID, ConnectionResponse> connections) {
        ConnectionResponse connection = isNull(account) ? null : connections.get(account.connectionId());
        return new SpendingListResponse.Item(
                transaction.id(),
                transaction.accountId(),
                transaction.bookedOn(),
                isNull(account) ? null : account.name(),
                isNull(connection) ? null : connection.institutionName(),
                isNull(connection) ? null : connection.institutionImageUrl(),
                transaction.description(),
                transaction.amount(),
                transaction.status(),
                transaction.category(),
                transaction.installmentNumber(),
                transaction.installmentTotal());
    }

    private static <T> Map<UUID, T> byId(List<T> values, Function<T, UUID> id) {
        return values.stream().collect(Collectors.toMap(id, Function.identity(), (first, second) -> first));
    }

    /** A part that only adds names: if it fails, the names are left out. A 401 still ends the call. */
    private static <T> List<T> optional(String name, CompletableFuture<List<T>> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof CoreErrorException coreError && coreError.isUnauthorized()) {
                throw coreError;
            }
            log.warn("[CALENDAR] {} unavailable, names left out: {}", name, ex.getCause().getMessage());
            return List.of();
        }
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            throw ex.getCause() instanceof RuntimeException runtime ? runtime : ex;
        }
    }
}
