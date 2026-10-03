package com.wallet.core.insight;

import com.wallet.core.MutableClock;
import com.wallet.core.TestClockConfiguration;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.banking.repository.AccountRepository;
import com.wallet.core.connection.ConnectionStatus;
import com.wallet.core.connection.entity.Connection;
import com.wallet.core.connection.repository.ConnectionRepository;
import com.wallet.core.provider.FinancialDataProvider;
import com.wallet.core.provider.ProviderAccount;
import com.wallet.core.provider.ProviderBill;
import com.wallet.core.provider.ProviderInvestment;
import com.wallet.core.provider.ProviderItem;
import com.wallet.core.provider.ProviderItemStatus;
import com.wallet.core.provider.ProviderTransaction;
import com.wallet.core.shared.finance.AccountKind;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.finance.InvestmentKind;
import com.wallet.core.shared.finance.TransactionStatus;
import com.wallet.core.shared.money.Money;
import com.wallet.core.sync.SyncRunStatus;
import com.wallet.core.sync.SyncTrigger;
import com.wallet.core.sync.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.wallet.core.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every read route over data written by a real sync (provider mocked). Today is 2026-10-01.
 * Checking 1000.00, card owing 500.00, investments 1000.00 + 250.00.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class ReadApiIT {

    @Autowired private WebApplicationContext context;
    @Autowired private MutableClock clock;
    @Autowired private SyncService syncService;
    @Autowired private ConnectionRepository connections;
    @Autowired private AccountRepository accounts;

    @MockitoBean
    private FinancialDataProvider provider;

    private MockMvc mockMvc;
    private UUID userId;
    private UUID connectionId;
    private String itemId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        clock.set(TestClockConfiguration.START);
        userId = TestUsers.register(mockMvc);
        itemId = UUID.randomUUID().toString();
        connectionId = saveConnection();
        stubProvider(Money.of("1000"));
        assertThat(syncService.syncNow(connectionId, SyncTrigger.MANUAL)).isEqualTo(SyncRunStatus.SUCCEEDED);
    }

    @Test
    void listsAccountsWithMoneyAsStrings() throws Exception {
        read("/internal/accounts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.kind == 'CHECKING')].balance").value("1000.00"))
                .andExpect(jsonPath("$[?(@.kind == 'CREDIT_CARD')].creditLimit").value("5000.00"));
    }

    @Test
    void statementDefaultsToTheCurrentMonth() throws Exception {
        read("/internal/transactions")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].bookedOn").value("2026-10-01"));
    }

    @Test
    void statementFiltersByAccountAndDirection() throws Exception {
        read("/internal/transactions?from=2026-09-01&to=2026-09-30&accountId=" + accountIdOf("card-1"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].installmentNumber").value(3))
                .andExpect(jsonPath("$.items[0].installmentTotal").value(10));

        read("/internal/transactions?from=2026-09-01&to=2026-09-30&direction=INFLOW")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].description").value("Salário"))
                .andExpect(jsonPath("$.items[0].amount").value("3200.00"));
    }

    @Test
    void textSearchIgnoresCaseAndAccents() throws Exception {
        read("/internal/transactions?from=2026-09-01&to=2026-09-30&q=SALARIO")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].description").value("Salário"));

        read("/internal/transactions?q=farmacia sao")
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void statementIsPaged() throws Exception {
        read("/internal/transactions?from=2026-09-01&to=2026-09-30&pageSize=2")
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                // Newest first.
                .andExpect(jsonPath("$.items[0].bookedOn").value("2026-09-20"));

        read("/internal/transactions?from=2026-09-01&to=2026-09-30&pageSize=2&page=3")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("page.invalid"));
    }

    @Test
    void rejectsBadPeriodsAndParameters() throws Exception {
        read("/internal/transactions?from=2026-09-30&to=2026-09-01")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("period.invalid"));
        read("/internal/transactions?from=2025-01-01&to=2026-09-30")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("period.too_long"));
        read("/internal/transactions?direction=SIDEWAYS")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.invalid_parameter"));
        read("/internal/insights/spending-by-category?month=setembro")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.invalid_parameter"));
    }

    @Test
    void creditCardShowsTheNextBillToPay() throws Exception {
        read("/internal/credit-cards")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].currentBalance").value("500.00"))
                .andExpect(jsonPath("$[0].availableCredit").value("4500.00"))
                .andExpect(jsonPath("$[0].currentBill.dueDate").value("2026-10-10"));

        read("/internal/credit-cards/" + accountIdOf("card-1") + "/bills")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].dueDate").value("2026-10-10"))
                .andExpect(jsonPath("$[1].totalAmount").value("300.00"));
    }

    @Test
    void billsOnlyForYourOwnCreditCard() throws Exception {
        read("/internal/credit-cards/" + accountIdOf("acc-1") + "/bills")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("account.not_found"));

        mockMvc.perform(get("/internal/credit-cards/" + accountIdOf("card-1") + "/bills").with(as(TestUsers.register(mockMvc))))
                .andExpect(status().isNotFound());
    }

    @Test
    void portfolioGroupsOpenPositionsByKind() throws Exception {
        read("/internal/investments")
                .andExpect(jsonPath("$.total").value("1250.00"))
                .andExpect(jsonPath("$.byKind[0].kind").value("FIXED_INCOME"))
                .andExpect(jsonPath("$.byKind[0].total").value("1000.00"))
                .andExpect(jsonPath("$.positions", hasSize(2)));
    }

    @Test
    void overviewAddsItAllUp() throws Exception {
        read("/internal/overview")
                .andExpect(jsonPath("$.cashBalance").value("1000.00"))
                .andExpect(jsonPath("$.creditCardDebt").value("500.00"))
                .andExpect(jsonPath("$.investmentsTotal").value("1250.00"))
                .andExpect(jsonPath("$.netWorth").value("1750.00"))
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.monthIncome").value("200.00"))
                .andExpect(jsonPath("$.monthExpenses").value("50.00"))
                .andExpect(jsonPath("$.lastSyncedAt").value("2026-10-01T12:00:00Z"));
    }

    @Test
    void spendingByCategoryLeavesCardBillPaymentsOut() throws Exception {
        read("/internal/insights/spending-by-category?month=2026-09")
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.total").value("165.90"))
                .andExpect(jsonPath("$.categories", hasSize(2)))
                .andExpect(jsonPath("$.categories[0].category").value("Shopping"))
                .andExpect(jsonPath("$.categories[1].total").value("45.90"));
    }

    /** The calendar: September had purchases on the 18th (card) and 20th; the bill paid on the 10th is not spending. */
    @Test
    void dailySpendingFollowsTheSameRuleAsCategories() throws Exception {
        read("/internal/insights/daily-spending?month=2026-09")
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.total").value("165.90"))
                .andExpect(jsonPath("$.days", hasSize(2)))
                .andExpect(jsonPath("$.days[0].date").value("2026-09-18"))
                .andExpect(jsonPath("$.days[0].total").value("120.00"))
                .andExpect(jsonPath("$.days[0].count").value(1))
                .andExpect(jsonPath("$.days[1].date").value("2026-09-20"));
    }

    @Test
    void aDaysSpendingListAddsUpToTheCalendar() throws Exception {
        read("/internal/transactions?from=2026-09-10&to=2026-09-10&spending=true")
                .andExpect(jsonPath("$.total").value(0));
        read("/internal/transactions?from=2026-09-01&to=2026-09-30&spending=true")
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].description").value("Padaria do Bairro"))
                .andExpect(jsonPath("$.items[1].description").value("Loja de Eletrônicos"));
        read("/internal/transactions?from=2026-09-10&to=2026-09-10")
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void netWorthCarriesTheLastKnownBalanceForward() throws Exception {
        read("/internal/insights/net-worth?from=2026-09-30&to=2026-10-01")
                .andExpect(jsonPath("$.points", hasSize(2)))
                .andExpect(jsonPath("$.points[0].netWorth").value("0.00"))
                .andExpect(jsonPath("$.points[1].netWorth").value("1750.00"));

        clock.advance(Duration.ofDays(2));
        stubProvider(Money.of("1100"));
        syncService.syncNow(connectionId, SyncTrigger.MANUAL);

        read("/internal/insights/net-worth?from=2026-10-01&to=2026-10-03")
                .andExpect(jsonPath("$.points[1].date").value("2026-10-02"))
                .andExpect(jsonPath("$.points[1].netWorth").value("1750.00"))
                .andExpect(jsonPath("$.points[2].netWorth").value("1850.00"))
                .andExpect(jsonPath("$.points[2].cash").value("1100.00"));
    }

    @Test
    void anotherUserSeesNothing() throws Exception {
        UUID stranger = TestUsers.register(mockMvc);

        mockMvc.perform(get("/internal/accounts").with(as(stranger))).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/internal/transactions?from=2026-09-01&to=2026-10-01").with(as(stranger)))
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/internal/overview").with(as(stranger)))
                .andExpect(jsonPath("$.netWorth").value("0.00"))
                .andExpect(jsonPath("$.lastSyncedAt").isEmpty());
    }

    private void stubProvider(Money checkingBalance) {
        when(provider.findItem(itemId)).thenReturn(new ProviderItem(itemId, "Nubank", null, ProviderItemStatus.READY,
                clock.instant(), null, null));
        when(provider.listAccounts(itemId)).thenReturn(List.of(
                new ProviderAccount("acc-1", itemId, AccountKind.CHECKING, "Conta", "3456", "BRL", checkingBalance, null, null),
                new ProviderAccount("card-1", itemId, AccountKind.CREDIT_CARD, "Cartão", "5162", "BRL",
                        Money.of("500"), Money.of("5000"), Money.of("4500"))));
        when(provider.listTransactions(eq("acc-1"), any(), any(), any())).thenReturn(List.of(
                transaction("tx-1", "2026-09-20", "45.90", Direction.OUTFLOW, "Padaria do Bairro", "Groceries", null),
                transaction("tx-2", "2026-09-05", "3200.00", Direction.INFLOW, "Salário", "Salary", null),
                transaction("tx-3", "2026-09-10", "300.00", Direction.OUTFLOW, "Pagamento fatura", "Credit card payment", null),
                transaction("tx-4", "2026-10-01", "200.00", Direction.INFLOW, "Pix recebido", "Transfers", null),
                transaction("tx-5", "2026-10-01", "50.00", Direction.OUTFLOW, "Farmácia São João", "Pharmacy", null)));
        when(provider.listTransactions(eq("card-1"), any(), any(), any())).thenReturn(List.of(
                transaction("card-tx-1", "2026-09-18", "120.00", Direction.OUTFLOW, "Loja de Eletrônicos", "Shopping", 3)));
        when(provider.listBills("card-1")).thenReturn(List.of(
                new ProviderBill("bill-old", "card-1", LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-03"),
                        Money.of("300"), Money.of("45"), "BRL"),
                new ProviderBill("bill-new", "card-1", LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-03"),
                        Money.of("500"), Money.of("75"), "BRL")));
        when(provider.listInvestments(itemId)).thenReturn(List.of(
                new ProviderInvestment("inv-1", itemId, InvestmentKind.FIXED_INCOME, "CDB", "CDB 110% CDI", "BRL",
                        Money.of("1000"), Money.of("900"), LocalDate.parse("2028-01-15"), true),
                new ProviderInvestment("inv-2", itemId, InvestmentKind.TREASURY, "TREASURY", "Tesouro Selic", "BRL",
                        Money.of("250"), Money.of("240"), LocalDate.parse("2029-03-01"), true)));
    }

    private static ProviderTransaction transaction(String id, String date, String amount, Direction direction,
                                                   String description, String category, Integer installment) {
        return new ProviderTransaction(id, "account", null, LocalDate.parse(date), description, Money.of(amount),
                direction, TransactionStatus.POSTED, category, installment, installment == null ? null : 10, null);
    }

    private UUID saveConnection() {
        Instant now = TestClockConfiguration.START;
        return connections.save(Connection.builder().id(UUID.randomUUID()).userId(userId).provider("PLUGGY")
                .providerItemId(itemId).institutionName("Nubank").status(ConnectionStatus.ACTIVE)
                .createdAt(now).updatedAt(now).build()).getId();
    }

    private UUID accountIdOf(String providerAccountId) {
        return accounts.findByConnectionIdAndProviderAccountId(connectionId, providerAccountId).orElseThrow().getId();
    }

    private ResultActions read(String path) throws Exception {
        return mockMvc.perform(get(path).with(as(userId)));
    }
}
