package com.wallet.bff.client;

import com.wallet.bff.model.dto.request.ExpectedSalaryRequest;
import com.wallet.bff.model.dto.request.FixedExpenseRequest;
import com.wallet.bff.model.dto.request.LinkConnectionRequest;
import com.wallet.bff.model.dto.request.LoginRequest;
import com.wallet.bff.model.dto.request.RefreshRequest;
import com.wallet.bff.model.dto.request.RegisterRequest;
import com.wallet.bff.model.dto.response.AccountResponse;
import com.wallet.bff.model.dto.response.BillResponse;
import com.wallet.bff.model.dto.response.CalendarResponse;
import com.wallet.bff.model.dto.response.ConnectTokenResponse;
import com.wallet.bff.model.dto.response.ConnectionResponse;
import com.wallet.bff.model.dto.response.CreditCardResponse;
import com.wallet.bff.model.dto.response.ExpectedSalaryResponse;
import com.wallet.bff.model.dto.response.FixedExpenseResponse;
import com.wallet.bff.model.dto.response.NetWorthResponse;
import com.wallet.bff.model.dto.response.OverviewResponse;
import com.wallet.bff.model.dto.response.PageResponse;
import com.wallet.bff.model.dto.response.PlanningResponse;
import com.wallet.bff.model.dto.response.PortfolioResponse;
import com.wallet.bff.model.dto.response.SpendingResponse;
import com.wallet.bff.model.dto.response.SyncStartedResponse;
import com.wallet.bff.model.dto.response.TransactionResponse;
import com.wallet.bff.model.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.nonNull;

/** The core's routes, typed. One method per route the BFF uses. */
@Component
@RequiredArgsConstructor
public class CoreApi {

    private final CoreClient client;

    public UserResponse register(RegisterRequest request) {
        return client.post("/internal/auth/register", request, new ParameterizedTypeReference<>() {});
    }

    public CoreTokens login(LoginRequest request) {
        return client.post("/internal/auth/login", request, new ParameterizedTypeReference<>() {});
    }

    public CoreTokens refresh(String refreshToken) {
        return client.post("/internal/auth/refresh", new RefreshRequest(refreshToken), new ParameterizedTypeReference<>() {});
    }

    public void logout(String refreshToken) {
        client.post("/internal/auth/logout", new RefreshRequest(refreshToken));
    }

    public UserResponse me() {
        return client.get("/internal/me", Map.of(), new ParameterizedTypeReference<>() {});
    }

    public List<ConnectionResponse> connections() {
        return client.get("/internal/connections", Map.of(), new ParameterizedTypeReference<>() {});
    }

    public ConnectionResponse link(LinkConnectionRequest request) {
        return client.post("/internal/connections", request, new ParameterizedTypeReference<>() {});
    }

    public ConnectTokenResponse connectToken() {
        return client.post("/internal/connections/connect-token", null, new ParameterizedTypeReference<>() {});
    }

    public void unlink(UUID connectionId) {
        client.delete("/internal/connections/" + connectionId);
    }

    public SyncStartedResponse sync(UUID connectionId) {
        return client.post("/internal/connections/" + connectionId + "/sync", null, new ParameterizedTypeReference<>() {});
    }

    public OverviewResponse overview() {
        return client.get("/internal/overview", Map.of(), new ParameterizedTypeReference<>() {});
    }

    public List<AccountResponse> accounts() {
        return client.get("/internal/accounts", Map.of(), new ParameterizedTypeReference<>() {});
    }

    /** Filters go through unparsed: the core validates them and answers in its own codes. */
    public PageResponse<TransactionResponse> transactions(Map<String, String> filters) {
        Map<String, Object> query = new LinkedHashMap<>();
        filters.forEach((name, value) -> {
            if (nonNull(value)) {
                query.put(name, value);
            }
        });
        return client.get("/internal/transactions", query, new ParameterizedTypeReference<>() {});
    }

    public List<CreditCardResponse> creditCards() {
        return client.get("/internal/credit-cards", Map.of(), new ParameterizedTypeReference<>() {});
    }

    public List<BillResponse> bills(UUID accountId) {
        return client.get("/internal/credit-cards/" + accountId + "/bills", Map.of(), new ParameterizedTypeReference<>() {});
    }

    public PortfolioResponse investments() {
        return client.get("/internal/investments", Map.of(), new ParameterizedTypeReference<>() {});
    }

    public SpendingResponse spending(String month) {
        Map<String, Object> query = nonNull(month) ? Map.of("month", month) : Map.of();
        return client.get("/internal/insights/spending-by-category", query, new ParameterizedTypeReference<>() {});
    }

    public CalendarResponse dailySpending(String month) {
        Map<String, Object> query = nonNull(month) ? Map.of("month", month) : Map.of();
        return client.get("/internal/insights/daily-spending", query, new ParameterizedTypeReference<>() {});
    }

    public NetWorthResponse netWorth(LocalDate from, LocalDate to) {
        return client.get("/internal/insights/net-worth", Map.of("from", from, "to", to),
                new ParameterizedTypeReference<>() {});
    }

    /** The month's plan as the core computes it, without the institutions' names. */
    public PlanningResponse planning(String month) {
        Map<String, Object> query = nonNull(month) ? Map.of("month", month) : Map.of();
        return client.get("/internal/planning/month", query, new ParameterizedTypeReference<>() {});
    }

    public List<FixedExpenseResponse> fixedExpenses() {
        return client.get("/internal/planning/fixed-expenses", Map.of(), new ParameterizedTypeReference<>() {});
    }

    public FixedExpenseResponse createFixedExpense(FixedExpenseRequest request) {
        return client.post("/internal/planning/fixed-expenses", request, new ParameterizedTypeReference<>() {});
    }

    public FixedExpenseResponse updateFixedExpense(UUID id, FixedExpenseRequest request) {
        return client.put("/internal/planning/fixed-expenses/" + id, request, new ParameterizedTypeReference<>() {});
    }

    public void deleteFixedExpense(UUID id) {
        client.delete("/internal/planning/fixed-expenses/" + id);
    }

    public ExpectedSalaryResponse setExpectedSalary(ExpectedSalaryRequest request) {
        return client.put("/internal/planning/expected-salary", request, new ParameterizedTypeReference<>() {});
    }

    public void clearExpectedSalary() {
        client.delete("/internal/planning/expected-salary");
    }
}
