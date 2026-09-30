package com.wallet.core.sync;

import com.jayway.jsonpath.JsonPath;
import com.wallet.core.MutableClock;
import com.wallet.core.TestClockConfiguration;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.banking.entity.AccountTransaction;
import com.wallet.core.banking.entity.BalanceSnapshot;
import com.wallet.core.banking.repository.AccountRepository;
import com.wallet.core.banking.repository.AccountTransactionRepository;
import com.wallet.core.banking.repository.BalanceSnapshotRepository;
import com.wallet.core.banking.repository.CreditCardBillRepository;
import com.wallet.core.connection.ConnectionStatus;
import com.wallet.core.connection.entity.Connection;
import com.wallet.core.connection.repository.ConnectionRepository;
import com.wallet.core.investment.entity.Investment;
import com.wallet.core.investment.repository.InvestmentRepository;
import com.wallet.core.provider.FinancialDataProvider;
import com.wallet.core.provider.ProviderAccount;
import com.wallet.core.provider.ProviderBill;
import com.wallet.core.provider.ProviderErrors;
import com.wallet.core.provider.ProviderInvestment;
import com.wallet.core.provider.ProviderItem;
import com.wallet.core.provider.ProviderItemStatus;
import com.wallet.core.provider.ProviderTransaction;
import com.wallet.core.shared.finance.AccountKind;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.finance.InvestmentKind;
import com.wallet.core.shared.finance.TransactionStatus;
import com.wallet.core.shared.money.Money;
import com.wallet.core.sync.entity.SyncRun;
import com.wallet.core.sync.repository.SyncRunRepository;
import com.wallet.core.sync.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.wallet.core.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The sync end to end against Postgres, with the provider mocked. Today is 2026-10-01 in São Paulo.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class SyncFlowIT {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-01");
    private static final Instant COLLECTED = Instant.parse("2026-10-01T06:00:00Z");

    @Autowired private WebApplicationContext context;
    @Autowired private MutableClock clock;
    @Autowired private SyncService syncService;
    @Autowired private ConnectionRepository connections;
    @Autowired private AccountRepository accounts;
    @Autowired private AccountTransactionRepository transactions;
    @Autowired private BalanceSnapshotRepository snapshots;
    @Autowired private CreditCardBillRepository bills;
    @Autowired private InvestmentRepository investments;
    @Autowired private SyncRunRepository runs;
    @Autowired private JdbcTemplate jdbc;

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
        connectionId = saveConnection(userId, itemId);
        stubItem(ProviderItemStatus.READY, COLLECTED);
    }

    @Test
    void firstSyncReadsAYearAndStoresEverything() {
        stubStandardData();

        assertThat(syncService.syncNow(connectionId, SyncTrigger.MANUAL)).isEqualTo(SyncRunStatus.SUCCEEDED);

        verify(provider).listTransactions("acc-1", AccountKind.CHECKING, LocalDate.parse("2025-10-01"), TODAY);
        verify(provider, never()).listBills("acc-1");
        assertThat(accountIds()).hasSize(2);
        assertThat(activeTransactions()).extracting(AccountTransaction::getProviderTransactionId)
                .containsExactlyInAnyOrder("tx-1", "tx-2", "card-tx-1");
        assertThat(bills.findAll()).anyMatch(bill -> bill.getProviderBillId().equals("bill-" + itemId));
        assertThat(investments.findByConnectionId(connectionId)).hasSize(1);

        UUID checkingId = accountIdOf("acc-1");
        assertThat(snapshots.findById(new BalanceSnapshot.Key(checkingId, TODAY)))
                .hasValueSatisfying(snapshot -> assertThat(snapshot.getBalance()).isEqualTo(Money.of("1000")));

        SyncRun run = lastRun();
        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.SUCCEEDED);
        assertThat(run.getAccountsCount()).isEqualTo(2);
        assertThat(run.getTransactionsUpserted()).isEqualTo(3);

        Connection connection = connections.findById(connectionId).orElseThrow();
        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.ACTIVE);
        assertThat(connection.getLastSyncedAt()).isEqualTo(TestClockConfiguration.START);
        assertThat(connection.getProviderUpdatedAt()).isEqualTo(COLLECTED);
    }

    @Test
    void laterSyncsReadFromAWeekBeforeTheLatestTransaction() {
        stubStandardData();
        syncService.syncNow(connectionId, SyncTrigger.MANUAL);

        clock.advance(Duration.ofDays(1));
        syncService.syncNow(connectionId, SyncTrigger.MANUAL);

        // Latest checking transaction is 2026-09-20; tomorrow is 2026-10-02.
        verify(provider).listTransactions("acc-1", AccountKind.CHECKING,
                LocalDate.parse("2026-09-13"), LocalDate.parse("2026-10-02"));
    }

    @Test
    void scheduledSyncSkipsWhenTheProviderHasNothingNew() {
        stubStandardData();
        syncService.syncNow(connectionId, SyncTrigger.MANUAL);
        clock.advance(Duration.ofHours(6));

        assertThat(syncService.syncNow(connectionId, SyncTrigger.SCHEDULED)).isEqualTo(SyncRunStatus.SKIPPED);
        assertThat(lastRun().getErrorCode()).isEqualTo("sync.nothing_new");
        verify(provider, times(1)).listAccounts(itemId);

        stubItem(ProviderItemStatus.READY, COLLECTED.plus(Duration.ofDays(1)));
        assertThat(syncService.syncNow(connectionId, SyncTrigger.SCHEDULED)).isEqualTo(SyncRunStatus.SUCCEEDED);
    }

    @Test
    void transactionWhoseIdChangedIsReplacedNotDuplicated() {
        when(provider.listAccounts(itemId)).thenReturn(List.of(checking()));
        when(provider.listTransactions(any(), any(), any(), any()))
                .thenReturn(List.of(transaction("old-id", "2026-09-25", "80.00", Direction.OUTFLOW, "MERCADO")))
                .thenReturn(List.of(transaction("new-id", "2026-09-25", "80.00", Direction.OUTFLOW, "MERCADO")));

        syncService.syncNow(connectionId, SyncTrigger.MANUAL);
        clock.advance(Duration.ofHours(6));
        syncService.syncNow(connectionId, SyncTrigger.MANUAL);

        assertThat(activeTransactions()).extracting(AccountTransaction::getProviderTransactionId).containsExactly("new-id");
        assertThat(transactions.findByAccountIdAndProviderTransactionId(accountIdOf("acc-1"), "old-id"))
                .hasValueSatisfying(old -> assertThat(old.getDeletedAt()).isNotNull());
        assertThat(lastRun().getTransactionsDeleted()).isEqualTo(1);
    }

    @Test
    void bankAskingForTheUserMarksTheConnection() {
        stubItem(ProviderItemStatus.NEEDS_USER_ACTION, COLLECTED);

        assertThat(syncService.syncNow(connectionId, SyncTrigger.MANUAL)).isEqualTo(SyncRunStatus.FAILED);

        assertThat(lastRun().getErrorCode()).isEqualTo("connection.needs_attention");
        assertThat(connections.findById(connectionId).orElseThrow().getStatus()).isEqualTo(ConnectionStatus.NEEDS_ATTENTION);
        verify(provider, never()).listAccounts(any());
    }

    @Test
    void onlyOneSyncPerConnectionRunsAtATime() {
        insertRunningRun(TestClockConfiguration.START.minus(Duration.ofMinutes(1)));

        assertThat(syncService.syncNow(connectionId, SyncTrigger.MANUAL)).isEqualTo(SyncRunStatus.SKIPPED);
        verify(provider, never()).findItem(any());
    }

    @Test
    void aSyncStuckInRunningIsReleasedAfterFifteenMinutes() {
        stubStandardData();
        insertRunningRun(TestClockConfiguration.START.minus(Duration.ofMinutes(20)));

        assertThat(syncService.syncNow(connectionId, SyncTrigger.MANUAL)).isEqualTo(SyncRunStatus.SUCCEEDED);
        assertThat(runs.findByConnectionIdOrderByStartedAtDesc(connectionId))
                .anyMatch(run -> "sync.timed_out".equals(run.getErrorCode()));
    }

    @Test
    void aFailureHalfwayWritesNothing() {
        stubStandardData();
        when(provider.listTransactions(any(), any(), any(), any()))
                .thenReturn(List.of(transaction("tx-1", "2026-09-20", "45.90", Direction.OUTFLOW, "PADARIA")))
                .thenThrow(ProviderErrors.unavailable("timeout"));

        assertThat(syncService.syncNow(connectionId, SyncTrigger.MANUAL)).isEqualTo(SyncRunStatus.FAILED);

        assertThat(lastRun().getErrorCode()).isEqualTo(ProviderErrors.UNAVAILABLE);
        assertThat(accountIds()).isEmpty();
        Connection connection = connections.findById(connectionId).orElseThrow();
        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.ACTIVE);
        assertThat(connection.getLastSyncedAt()).isNull();
    }

    @Test
    void descriptionsAreEncryptedAtRest() {
        stubStandardData();
        syncService.syncNow(connectionId, SyncTrigger.MANUAL);

        String stored = jdbc.queryForObject(
                "select description from transactions where provider_transaction_id = 'tx-1' and account_id = ?",
                String.class, accountIdOf("acc-1"));

        assertThat(stored).startsWith("v1:").doesNotContain("PADARIA");
        assertThat(transactions.findByAccountIdAndProviderTransactionId(accountIdOf("acc-1"), "tx-1"))
                .hasValueSatisfying(transaction -> assertThat(transaction.getDescription()).isEqualTo("PADARIA DO BAIRRO"));
    }

    @Test
    void investmentsThatDisappearAreClosed() {
        when(provider.listAccounts(itemId)).thenReturn(List.of());
        when(provider.listInvestments(itemId))
                .thenReturn(List.of(investment("inv-1"), investment("inv-2")))
                .thenReturn(List.of(investment("inv-1")));

        syncService.syncNow(connectionId, SyncTrigger.MANUAL);
        syncService.syncNow(connectionId, SyncTrigger.MANUAL);

        assertThat(investments.findByConnectionId(connectionId))
                .filteredOn(investment -> investment.getClosedAt() == null)
                .extracting(Investment::getProviderInvestmentId)
                .containsExactly("inv-1");
    }

    @Test
    void manualSyncRunsInTheBackgroundAndIsRateLimited() throws Exception {
        stubStandardData();

        mockMvc.perform(post("/internal/connections/" + connectionId + "/sync").with(as(userId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.syncRunId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("RUNNING"));
        assertThat(awaitFinished().getStatus()).isEqualTo(SyncRunStatus.SUCCEEDED);

        mockMvc.perform(post("/internal/connections/" + connectionId + "/sync").with(as(userId)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("sync.too_soon"));
    }

    @Test
    void manualSyncWhileOneIsRunningIsAConflict() throws Exception {
        insertRunningRun(TestClockConfiguration.START);

        mockMvc.perform(post("/internal/connections/" + connectionId + "/sync").with(as(userId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("sync.in_progress"));
    }

    @Test
    void nobodyCanSyncSomeoneElsesConnection() throws Exception {
        mockMvc.perform(post("/internal/connections/" + connectionId + "/sync").with(as(TestUsers.register(mockMvc))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("connection.not_found"));
    }

    @Test
    void linkingAConnectionTriggersTheFirstSync() throws Exception {
        String newItemId = UUID.randomUUID().toString();
        when(provider.findItem(newItemId)).thenReturn(new ProviderItem(newItemId, "Inter", null,
                ProviderItemStatus.READY, COLLECTED, null, null));

        String body = mockMvc.perform(post("/internal/connections").with(as(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerItemId\":\"%s\"}".formatted(newItemId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        connectionId = UUID.fromString(JsonPath.read(body, "$.id"));

        SyncRun run = awaitFinished();
        assertThat(run.getTrigger()).isEqualTo(SyncTrigger.INITIAL);
        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.SUCCEEDED);
    }

    @Test
    void unlinkingRemovesEverythingThatWasSynced() throws Exception {
        stubStandardData();
        syncService.syncNow(connectionId, SyncTrigger.MANUAL);

        mockMvc.perform(delete("/internal/connections/" + connectionId).with(as(userId)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from accounts where connection_id = ?", Integer.class, connectionId))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from transactions where user_id = ?", Integer.class, userId))
                .isZero();
    }

    private void stubStandardData() {
        when(provider.listAccounts(itemId)).thenReturn(List.of(checking(), card()));
        when(provider.listTransactions("acc-1", AccountKind.CHECKING, LocalDate.parse("2025-10-01"), TODAY))
                .thenReturn(List.of(
                        transaction("tx-1", "2026-09-20", "45.90", Direction.OUTFLOW, "PADARIA DO BAIRRO"),
                        transaction("tx-2", "2026-09-05", "3200.00", Direction.INFLOW, "SALARIO")));
        when(provider.listTransactions("acc-1", AccountKind.CHECKING, LocalDate.parse("2026-09-13"), LocalDate.parse("2026-10-02")))
                .thenReturn(List.of(transaction("tx-1", "2026-09-20", "45.90", Direction.OUTFLOW, "PADARIA DO BAIRRO")));
        when(provider.listTransactions("card-1", AccountKind.CREDIT_CARD, LocalDate.parse("2025-10-01"), TODAY))
                .thenReturn(List.of(transaction("card-tx-1", "2026-09-18", "120.00", Direction.OUTFLOW, "LOJA")));
        when(provider.listBills("card-1")).thenReturn(List.of(new ProviderBill("bill-" + itemId, "card-1",
                LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-03"), Money.of("500"), Money.of("75"), "BRL")));
        when(provider.listInvestments(itemId)).thenReturn(List.of(investment("inv-1")));
    }

    private void stubItem(ProviderItemStatus status, Instant lastUpdatedAt) {
        when(provider.findItem(itemId)).thenReturn(new ProviderItem(itemId, "Nubank", null, status, lastUpdatedAt, null, null));
    }

    private ProviderAccount checking() {
        return new ProviderAccount("acc-1", itemId, AccountKind.CHECKING, "Conta Corrente", "3456", "BRL",
                Money.of("1000"), null, null);
    }

    private ProviderAccount card() {
        return new ProviderAccount("card-1", itemId, AccountKind.CREDIT_CARD, "Cartão", "5162", "BRL",
                Money.of("500"), Money.of("5000"), Money.of("4500"));
    }

    private static ProviderTransaction transaction(String id, String date, String amount, Direction direction, String description) {
        return new ProviderTransaction(id, "acc", null, LocalDate.parse(date), description, Money.of(amount), direction,
                TransactionStatus.POSTED, null, null, null, null);
    }

    private ProviderInvestment investment(String id) {
        return new ProviderInvestment(id, itemId, InvestmentKind.FIXED_INCOME, "CDB", "CDB " + id, "BRL",
                Money.of("1000"), Money.of("900"), LocalDate.parse("2028-01-01"), true);
    }

    private UUID saveConnection(UUID owner, String providerItemId) {
        Instant now = TestClockConfiguration.START;
        return connections.save(Connection.builder()
                .id(UUID.randomUUID())
                .userId(owner)
                .provider("PLUGGY")
                .providerItemId(providerItemId)
                .institutionName("Nubank")
                .status(ConnectionStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build()).getId();
    }

    private void insertRunningRun(Instant startedAt) {
        jdbc.update("insert into sync_runs (id, connection_id, trigger, status, started_at) values (?, ?, 'MANUAL', 'RUNNING', ?)",
                UUID.randomUUID(), connectionId, Timestamp.from(startedAt));
    }

    private List<UUID> accountIds() {
        return jdbc.queryForList("select id from accounts where connection_id = ?", UUID.class, connectionId);
    }

    private UUID accountIdOf(String providerAccountId) {
        return accounts.findByConnectionIdAndProviderAccountId(connectionId, providerAccountId).orElseThrow().getId();
    }

    private List<AccountTransaction> activeTransactions() {
        return transactions.findAll().stream()
                .filter(transaction -> transaction.getUserId().equals(userId) && transaction.getDeletedAt() == null)
                .toList();
    }

    private SyncRun lastRun() {
        return runs.findByConnectionIdOrderByStartedAtDesc(connectionId).getFirst();
    }

    /** Background syncs finish in milliseconds; ten seconds is only a safety net. */
    private SyncRun awaitFinished() throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            List<SyncRun> found = runs.findByConnectionIdOrderByStartedAtDesc(connectionId);
            if (!found.isEmpty() && !SyncRunStatus.RUNNING.equals(found.getFirst().getStatus())) {
                return found.getFirst();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Sync of connection " + connectionId + " did not finish");
    }
}
