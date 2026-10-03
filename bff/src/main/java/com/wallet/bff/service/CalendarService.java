package com.wallet.bff.service;

import com.wallet.bff.cache.ScreenCache;
import com.wallet.bff.client.CoreApi;
import com.wallet.bff.config.RequestContextExecutor;
import com.wallet.bff.exception.CoreErrorException;
import com.wallet.bff.model.dto.response.AccountResponse;
import com.wallet.bff.model.dto.response.CalendarResponse;
import com.wallet.bff.model.dto.response.ConnectionResponse;
import com.wallet.bff.model.dto.response.DaySpendingResponse;
import com.wallet.bff.model.dto.response.PageResponse;
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

/** The spending calendar on the home screen: which days had spending, and what, and at which bank. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarService {

    /** The core's largest page; a single day does not come near it. */
    private static final String DAY_PAGE_SIZE = "200";

    private final CoreApi coreApi;
    private final RequestContextExecutor executor;
    private final ScreenCache cache;

    public CalendarResponse month(UUID userId, String month) {
        return cache.get(userId, "calendar", isNull(month) ? "" : month, () -> coreApi.dailySpending(month));
    }

    /**
     * The day's spending (same rule as the calendar, so it adds up to the day's total) with the
     * account and bank names. Without the names the list still comes back; without the list
     * there is nothing to show.
     */
    public DaySpendingResponse day(LocalDate date) {
        CompletableFuture<PageResponse<TransactionResponse>> spending = executor.supply(() -> coreApi.transactions(Map.of(
                "from", date.toString(),
                "to", date.toString(),
                "spending", "true",
                "pageSize", DAY_PAGE_SIZE)));
        CompletableFuture<List<AccountResponse>> accounts = executor.supply(coreApi::accounts);
        CompletableFuture<List<ConnectionResponse>> connections = executor.supply(coreApi::connections);

        PageResponse<TransactionResponse> page = join(spending);
        Map<UUID, AccountResponse> accountsById = byId(optional("accounts", accounts), AccountResponse::id);
        Map<UUID, ConnectionResponse> connectionsById = byId(optional("connections", connections), ConnectionResponse::id);

        return new DaySpendingResponse(date, page.items().stream()
                .map(transaction -> item(transaction, accountsById.get(transaction.accountId()), connectionsById))
                .toList());
    }

    private static DaySpendingResponse.Item item(TransactionResponse transaction, AccountResponse account,
                                                 Map<UUID, ConnectionResponse> connections) {
        ConnectionResponse connection = isNull(account) ? null : connections.get(account.connectionId());
        return new DaySpendingResponse.Item(
                transaction.id(),
                transaction.accountId(),
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
