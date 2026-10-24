package com.wallet.bff.client;

import com.wallet.bff.model.dto.request.AccountDeletionRequest;
import com.wallet.bff.model.dto.request.EmailChangeRequest;
import com.wallet.bff.model.dto.request.EmailTokenRequest;
import com.wallet.bff.model.dto.request.NewRecoveryCodesRequest;
import com.wallet.bff.model.dto.request.PasswordChangeRequest;
import com.wallet.bff.model.dto.request.PasswordResetRequest;
import com.wallet.bff.model.dto.request.ForgotPasswordRequest;
import com.wallet.bff.model.dto.request.ExpectedSalaryRequest;
import com.wallet.bff.model.dto.request.FixedExpenseRequest;
import com.wallet.bff.model.dto.request.LinkConnectionRequest;
import com.wallet.bff.model.dto.request.LoginRequest;
import com.wallet.bff.model.dto.request.ProfileRequest;
import com.wallet.bff.model.dto.request.RefreshRequest;
import com.wallet.bff.model.dto.request.RegisterRequest;
import com.wallet.bff.model.dto.request.ResendConfirmationRequest;
import com.wallet.bff.model.dto.request.TwoFactorCodeRequest;
import com.wallet.bff.model.dto.request.TwoFactorDisableRequest;
import com.wallet.bff.model.dto.request.TwoFactorSetupRequest;
import com.wallet.bff.model.dto.request.WaitlistEmailRequest;
import com.wallet.bff.model.dto.request.WaitlistTokenRequest;
import com.wallet.bff.model.dto.response.AccountResponse;
import com.wallet.bff.model.dto.response.BillResponse;
import com.wallet.bff.model.dto.response.CalendarResponse;
import com.wallet.bff.model.dto.response.ConnectTokenResponse;
import com.wallet.bff.model.dto.response.ConnectionResponse;
import com.wallet.bff.model.dto.response.CreditCardResponse;
import com.wallet.bff.model.dto.response.EmailConfirmationResponse;
import com.wallet.bff.model.dto.response.ExpectedSalaryResponse;
import com.wallet.bff.model.dto.response.FixedExpenseResponse;
import com.wallet.bff.model.dto.response.FixedExpenseSuggestionResponse;
import com.wallet.bff.model.dto.response.NetWorthResponse;
import com.wallet.bff.model.dto.response.OverviewResponse;
import com.wallet.bff.model.dto.response.PageResponse;
import com.wallet.bff.model.dto.response.PlanningResponse;
import com.wallet.bff.model.dto.response.PortfolioResponse;
import com.wallet.bff.model.dto.response.RecoveryCodesResponse;
import com.wallet.bff.model.dto.response.SpendingResponse;
import com.wallet.bff.model.dto.response.SyncStartedResponse;
import com.wallet.bff.model.dto.response.TransactionResponse;
import com.wallet.bff.model.dto.response.TwoFactorSetupResponse;
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
import static org.springframework.util.StringUtils.hasText;

/** The core's routes, typed. One method per route the BFF uses, each in its {@link CoreRouteGroup}. */
@Component
@RequiredArgsConstructor
public class CoreApi {

    private final CoreClient client;

    /** 202 and no body, whatever the e-mail: the core never tells whether it had an account. */
    public void register(RegisterRequest request) {
        client.post(CoreRouteGroup.AUTH, "/internal/auth/register", request);
    }

    public EmailConfirmationResponse confirmEmail(EmailTokenRequest request) {
        return client.post(CoreRouteGroup.AUTH, "/internal/auth/email/confirm", request, new ParameterizedTypeReference<>() {});
    }

    public void resendConfirmation(ResendConfirmationRequest request) {
        client.post(CoreRouteGroup.AUTH, "/internal/auth/email/resend", request);
    }

    /** 202 and no body, whatever the e-mail: the link goes only to a confirmed account, and nobody learns which. */
    public void forgotPassword(ForgotPasswordRequest request) {
        client.post(CoreRouteGroup.AUTH, "/internal/auth/password/forgot", request);
    }

    public void resetPassword(PasswordResetRequest request) {
        client.post(CoreRouteGroup.AUTH, "/internal/auth/password/reset", request);
    }

    /** Checks the current password again (a BCrypt), so it is in the AUTH group like the e-mail change. */
    public void changePassword(PasswordChangeRequest request) {
        client.put(CoreRouteGroup.AUTH, "/internal/me/password", request);
    }

    public RecoveryCodesResponse newRecoveryCodes(NewRecoveryCodesRequest request) {
        return client.post(CoreRouteGroup.AUTH, "/internal/me/two-factor/recovery-codes", request, new ParameterizedTypeReference<>() {});
    }

    /** 202 and no body, whatever the e-mail: the core never tells whether it was already on the list. */
    public void subscribeToWaitlist(WaitlistEmailRequest request) {
        client.post(CoreRouteGroup.AUTH, "/internal/waitlist", request);
    }

    public void confirmWaitlist(WaitlistTokenRequest request) {
        client.post(CoreRouteGroup.AUTH, "/internal/waitlist/confirm", request);
    }

    public void unsubscribeFromWaitlist(WaitlistTokenRequest request) {
        client.post(CoreRouteGroup.AUTH, "/internal/waitlist/unsubscribe", request);
    }

    public CoreTokens login(LoginRequest request) {
        return client.post(CoreRouteGroup.AUTH, "/internal/auth/login", request, new ParameterizedTypeReference<>() {});
    }

    public CoreTokens refresh(String refreshToken) {
        return client.post(CoreRouteGroup.AUTH, "/internal/auth/refresh", new RefreshRequest(refreshToken), new ParameterizedTypeReference<>() {});
    }

    public void logout(String refreshToken) {
        client.post(CoreRouteGroup.AUTH, "/internal/auth/logout", new RefreshRequest(refreshToken));
    }

    public UserResponse me() {
        return client.get(CoreRouteGroup.READS, "/internal/me", Map.of(), new ParameterizedTypeReference<>() {});
    }

    public void deleteMe(AccountDeletionRequest request) {
        client.deleteWaitingOnProvider("/internal/me", request);
    }

    public UserResponse updateMe(ProfileRequest request) {
        return client.put(CoreRouteGroup.READS, "/internal/me", request, new ParameterizedTypeReference<>() {});
    }

    public void changeEmail(EmailChangeRequest request) {
        client.post(CoreRouteGroup.AUTH, "/internal/me/email", request);
    }

    public TwoFactorSetupResponse setUpTwoFactor(TwoFactorSetupRequest request) {
        return client.post(CoreRouteGroup.AUTH, "/internal/me/two-factor/setup", request, new ParameterizedTypeReference<>() {});
    }

    public RecoveryCodesResponse enableTwoFactor(TwoFactorCodeRequest request) {
        return client.post(CoreRouteGroup.AUTH, "/internal/me/two-factor/enable", request, new ParameterizedTypeReference<>() {});
    }

    public void disableTwoFactor(TwoFactorDisableRequest request) {
        client.deleteWithBody(CoreRouteGroup.AUTH, "/internal/me/two-factor", request);
    }

    public List<ConnectionResponse> connections() {
        return client.get(CoreRouteGroup.READS, "/internal/connections", Map.of(), new ParameterizedTypeReference<>() {});
    }

    public ConnectionResponse link(LinkConnectionRequest request) {
        return client.post(CoreRouteGroup.PROVIDER, "/internal/connections", request, new ParameterizedTypeReference<>() {});
    }

    public ConnectTokenResponse connectToken() {
        return client.post(CoreRouteGroup.PROVIDER, "/internal/connections/connect-token", null, new ParameterizedTypeReference<>() {});
    }

    public void unlink(UUID connectionId) {
        client.delete(CoreRouteGroup.PROVIDER, "/internal/connections/" + connectionId);
    }

    public SyncStartedResponse sync(UUID connectionId) {
        return client.post(CoreRouteGroup.PROVIDER, "/internal/connections/" + connectionId + "/sync", null, new ParameterizedTypeReference<>() {});
    }

    public OverviewResponse overview() {
        return client.get(CoreRouteGroup.SCREENS, "/internal/overview", Map.of(), new ParameterizedTypeReference<>() {});
    }

    public List<AccountResponse> accounts() {
        return client.get(CoreRouteGroup.READS, "/internal/accounts", Map.of(), new ParameterizedTypeReference<>() {});
    }

    /**
     * Filters go through unparsed: the core validates them and answers in its own codes. A text
     * search is its own group: the core decrypts the period to answer it.
     */
    public PageResponse<TransactionResponse> transactions(Map<String, String> filters) {
        Map<String, Object> query = new LinkedHashMap<>();
        filters.forEach((name, value) -> {
            if (nonNull(value)) {
                query.put(name, value);
            }
        });
        CoreRouteGroup group = hasText(filters.get("q")) ? CoreRouteGroup.SEARCH : CoreRouteGroup.READS;
        return client.get(group, "/internal/transactions", query, new ParameterizedTypeReference<>() {});
    }

    public List<CreditCardResponse> creditCards() {
        return client.get(CoreRouteGroup.READS, "/internal/credit-cards", Map.of(), new ParameterizedTypeReference<>() {});
    }

    public List<BillResponse> bills(UUID accountId) {
        return client.get(CoreRouteGroup.READS, "/internal/credit-cards/" + accountId + "/bills", Map.of(), new ParameterizedTypeReference<>() {});
    }

    public PortfolioResponse investments() {
        return client.get(CoreRouteGroup.READS, "/internal/investments", Map.of(), new ParameterizedTypeReference<>() {});
    }

    public SpendingResponse spending(String month) {
        Map<String, Object> query = nonNull(month) ? Map.of("month", month) : Map.of();
        return client.get(CoreRouteGroup.SCREENS, "/internal/insights/spending-by-category", query, new ParameterizedTypeReference<>() {});
    }

    public CalendarResponse dailySpending(String month) {
        Map<String, Object> query = nonNull(month) ? Map.of("month", month) : Map.of();
        return client.get(CoreRouteGroup.SCREENS, "/internal/insights/daily-spending", query, new ParameterizedTypeReference<>() {});
    }

    public NetWorthResponse netWorth(LocalDate from, LocalDate to) {
        return client.get(CoreRouteGroup.SCREENS, "/internal/insights/net-worth", Map.of("from", from, "to", to),
                new ParameterizedTypeReference<>() {});
    }

    /** The month's plan as the core computes it, without the institutions' names. */
    public PlanningResponse planning(String month) {
        Map<String, Object> query = nonNull(month) ? Map.of("month", month) : Map.of();
        return client.get(CoreRouteGroup.SCREENS, "/internal/planning/month", query, new ParameterizedTypeReference<>() {});
    }

    public List<FixedExpenseResponse> fixedExpenses() {
        return client.get(CoreRouteGroup.READS, "/internal/planning/fixed-expenses", Map.of(), new ParameterizedTypeReference<>() {});
    }

    public FixedExpenseResponse createFixedExpense(FixedExpenseRequest request) {
        return client.post(CoreRouteGroup.READS, "/internal/planning/fixed-expenses", request, new ParameterizedTypeReference<>() {});
    }

    public FixedExpenseResponse updateFixedExpense(UUID id, FixedExpenseRequest request) {
        return client.put(CoreRouteGroup.READS, "/internal/planning/fixed-expenses/" + id, request, new ParameterizedTypeReference<>() {});
    }

    public void deleteFixedExpense(UUID id) {
        client.delete(CoreRouteGroup.READS, "/internal/planning/fixed-expenses/" + id);
    }

    public List<FixedExpenseSuggestionResponse> fixedExpenseSuggestions() {
        return client.get(CoreRouteGroup.SCREENS, "/internal/planning/fixed-expense-suggestions", Map.of(), new ParameterizedTypeReference<>() {});
    }

    /** The id is the app's to send and the core's to check: it travels as a URI variable. */
    public void dismissFixedExpenseSuggestion(String id) {
        client.delete(CoreRouteGroup.READS, "/internal/planning/fixed-expense-suggestions/{id}", id);
    }

    public ExpectedSalaryResponse setExpectedSalary(ExpectedSalaryRequest request) {
        return client.put(CoreRouteGroup.READS, "/internal/planning/expected-salary", request, new ParameterizedTypeReference<>() {});
    }

    public void clearExpectedSalary() {
        client.delete(CoreRouteGroup.READS, "/internal/planning/expected-salary");
    }
}
