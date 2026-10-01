package com.wallet.bff.service;

import com.wallet.bff.cache.ScreenCache;
import com.wallet.bff.client.CoreApi;
import com.wallet.bff.config.ClockConfig;
import com.wallet.bff.config.RequestContextExecutor;
import com.wallet.bff.model.dto.response.BillResponse;
import com.wallet.bff.model.dto.response.CreditCardResponse;
import com.wallet.bff.model.dto.response.InsightsResponse;
import com.wallet.bff.model.dto.response.NetWorthResponse;
import com.wallet.bff.model.dto.response.PageResponse;
import com.wallet.bff.model.dto.response.PortfolioResponse;
import com.wallet.bff.model.dto.response.SpendingResponse;
import com.wallet.bff.model.dto.response.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static java.util.Objects.isNull;

/** The other screens. The statement and bills are not cached: they are paged and filtered per call. */
@Service
@RequiredArgsConstructor
public class ScreenService {

    private static final int NET_WORTH_DAYS = 183;

    private final CoreApi coreApi;
    private final RequestContextExecutor executor;
    private final ScreenCache cache;
    private final Clock clock;

    public PageResponse<TransactionResponse> transactions(Map<String, String> filters) {
        return coreApi.transactions(filters);
    }

    public List<CreditCardResponse> cards(UUID userId) {
        return cache.get(userId, "cards", "", coreApi::creditCards);
    }

    public List<BillResponse> bills(UUID accountId) {
        return coreApi.bills(accountId);
    }

    public PortfolioResponse investments(UUID userId) {
        return cache.get(userId, "investments", "", coreApi::investments);
    }

    /** The month's spending and six months of net worth, fetched side by side. */
    public InsightsResponse insights(UUID userId, String month) {
        return cache.get(userId, "insights", isNull(month) ? "" : month, () -> {
            LocalDate today = ClockConfig.today(clock);
            CompletableFuture<SpendingResponse> spending = executor.supply(() -> coreApi.spending(month));
            CompletableFuture<NetWorthResponse> netWorth =
                    executor.supply(() -> coreApi.netWorth(today.minusDays(NET_WORTH_DAYS - 1), today));
            return new InsightsResponse(join(spending), join(netWorth));
        });
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            throw ex.getCause() instanceof RuntimeException runtime ? runtime : ex;
        }
    }
}
