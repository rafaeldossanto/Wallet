package com.wallet.core.identity;

import com.jayway.jsonpath.JsonPath;
import com.wallet.core.TestMockMvc;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.identity.repository.UserRepository;
import com.wallet.core.provider.FinancialDataProvider;
import com.wallet.core.provider.ProviderErrors;
import com.wallet.core.provider.ProviderItem;
import com.wallet.core.provider.ProviderItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.wallet.core.TestUsers.as;
import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deleting an account in the default MEU_PLUGGY mode, with the provider mocked: the items are let
 * go of at the provider first, then every row of the user goes with the cascade.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountDeletionIT {

    private static final String PASSWORD = TestUsers.PASSWORD;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private FinancialDataProvider provider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = TestMockMvc.of(context);
    }

    @Test
    void deletesTheAccountAndEverythingInItButNothingOfAnotherUser() throws Exception {
        Registered owner = register();
        Registered other = register();
        Seeded mine = seed(owner.id());
        Seeded theirs = seed(other.id());
        login(owner.email());
        stubItem(mine.widgetItem(), owner.id());
        stubItem(mine.meuPluggyItem(), null);

        deleteAccount(owner.id(), PASSWORD)
                .andExpect(status().isNoContent());

        assertThat(rowsOf(owner.id(), mine)).allSatisfy((table, rows) -> assertThat(rows).as(table).isZero());
        assertThat(rowsOf(other.id(), theirs)).allSatisfy((table, rows) -> assertThat(rows).as(table).isPositive());
        assertThat(count("select count(*) from refresh_tokens where user_id = ?", owner.id())).isZero();
        // Kept on purpose: no foreign key, no financial data, 180-day retention.
        assertThat(count("select count(*) from provider_calls where user_id = ?", owner.id())).isOne();
    }

    /** Meu Pluggy items belong to the Meu Pluggy account: only the widget's item is revoked. */
    @Test
    void revokesTheItemsTheWalletCreatedBeforeTheDataGoes() throws Exception {
        Registered owner = register();
        Seeded mine = seed(owner.id());
        stubItem(mine.widgetItem(), owner.id());
        stubItem(mine.meuPluggyItem(), null);

        deleteAccount(owner.id(), PASSWORD)
                .andExpect(status().isNoContent());

        verify(provider).deleteItem(mine.widgetItem());
        verify(provider, never()).deleteItem(mine.meuPluggyItem());
    }

    @Test
    void aProviderFailureDoesNotStopTheDeletion() throws Exception {
        Registered owner = register();
        Seeded mine = seed(owner.id());
        stubItem(mine.widgetItem(), owner.id());
        stubItem(mine.meuPluggyItem(), owner.id());
        doThrow(ProviderErrors.unavailable("down")).when(provider).deleteItem(mine.widgetItem());

        deleteAccount(owner.id(), PASSWORD)
                .andExpect(status().isNoContent());

        assertThat(users.existsById(owner.id())).isFalse();
        assertThat(rowsOf(owner.id(), mine)).allSatisfy((table, rows) -> assertThat(rows).as(table).isZero());
        // The next connection was still tried.
        verify(provider).deleteItem(mine.meuPluggyItem());
    }

    @Test
    void deletingTwiceIsHarmlessAndTheSessionIsGone() throws Exception {
        Registered owner = register();
        String refreshToken = login(owner.email());

        deleteAccount(owner.id(), PASSWORD).andExpect(status().isNoContent());
        deleteAccount(owner.id(), PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(get("/internal/me").with(as(owner.id())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_refresh_token"));
        mockMvc.perform(post("/internal/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(owner.email(), PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * A JWT cannot be revoked: the deleted user's access token still passes its signature and expiry
     * for up to 15 minutes. Every request with it must be a clean 401 (the app then finds its
     * refresh token gone and shows the sign-in), not a 500 from a write that hits the foreign key of
     * a user who no longer exists (L1-c).
     */
    @Test
    void theAccessTokenOfADeletedAccountGetsACleanUnauthorized() throws Exception {
        Registered owner = register();
        String accessToken = accessToken(owner.email());

        deleteAccount(owner.id(), PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(put("/internal/planning/expected-salary").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"3500.00\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthenticated"));
        mockMvc.perform(get("/internal/planning/fixed-expenses").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthenticated"));
    }

    @Test
    void withoutATokenNothingIsDeleted() throws Exception {
        Registered owner = register();

        mockMvc.perform(delete("/internal/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());

        assertThat(users.existsById(owner.id())).isTrue();
    }

    /** The token alone is not enough: a wrong password deletes nothing and counts toward the lockout. */
    @Test
    void aWrongPasswordDeletesNothingAndCountsTowardTheLockout() throws Exception {
        Registered owner = register();
        Seeded mine = seed(owner.id());
        stubItem(mine.widgetItem(), owner.id());

        for (int attempt = 1; attempt < 5; attempt++) {
            deleteAccount(owner.id(), "not-the-password")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("auth.wrong_password"));
        }
        // The fifth says the address is now locked out of the account, not "wrong password" again.
        deleteAccount(owner.id(), "not-the-password")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("auth.locked"))
                .andExpect(jsonPath("$.lockedUntil").isString());
        deleteAccount(owner.id(), PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("auth.locked"))
                .andExpect(jsonPath("$.lockedUntil").isString());

        assertThat(users.existsById(owner.id())).isTrue();
        assertThat(rowsOf(owner.id(), mine)).allSatisfy((table, rows) -> assertThat(rows).as(table).isPositive());
        verify(provider, never()).deleteItem(mine.widgetItem());
    }

    @Test
    void withoutThePasswordNothingIsDeleted() throws Exception {
        Registered owner = register();

        mockMvc.perform(delete("/internal/me").with(as(owner.id())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.malformed"));
        mockMvc.perform(delete("/internal/me").with(as(owner.id())).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(users.existsById(owner.id())).isTrue();
    }

    /**
     * The deletion locks the user's row FOR UPDATE: a connection being linked meanwhile has to
     * wait for it (and then fails on the foreign key), so none is cascaded away unrevoked.
     */
    @Test
    void theDeletionLockMakesAConcurrentLinkWait() throws Exception {
        Registered owner = register();
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = Thread.ofVirtual().start(() -> transactions.executeWithoutResult(transaction -> {
            users.findByIdLockingReferences(owner.id());
            locked.countDown();
            awaitQuietly(release);
        }));
        try {
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> transactions.executeWithoutResult(transaction -> {
                jdbc.execute("set local lock_timeout = '300ms'");
                insertConnection(UUID.randomUUID(), owner.id(), "item-" + UUID.randomUUID());
            })).isInstanceOf(DataAccessException.class);
        } finally {
            release.countDown();
            holder.join();
        }
    }

    private ResultActions deleteAccount(UUID userId, String password) throws Exception {
        return mockMvc.perform(delete("/internal/me").with(as(userId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"%s\"}".formatted(password)));
    }

    private Registered register() throws Exception {
        String email = "delete+" + UUID.randomUUID() + "@example.com";
        return new Registered(TestUsers.register(mockMvc, email), email);
    }

    private String login(String email) throws Exception {
        return JsonPath.read(loginBody(email), "$.refreshToken");
    }

    private String accessToken(String email) throws Exception {
        return JsonPath.read(loginBody(email), "$.accessToken");
    }

    private String loginBody(String email) throws Exception {
        return mockMvc.perform(post("/internal/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void stubItem(String itemId, UUID clientUserId) {
        when(provider.findItem(itemId)).thenReturn(new ProviderItem(itemId, "Nubank", null, ProviderItemStatus.READY,
                Instant.parse("2026-10-01T09:00:00Z"), null, isNull(clientUserId) ? null : clientUserId.toString()));
    }

    /**
     * One row in every table a user has data in. Connections wait on the user (NEEDS_ATTENTION),
     * so the scheduler leaves them alone.
     */
    private Seeded seed(UUID userId) {
        Timestamp now = Timestamp.from(Instant.now());
        Date day = Date.valueOf(LocalDate.of(2026, 10, 1));
        Seeded seeded = new Seeded(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "item-" + UUID.randomUUID(), "item-" + UUID.randomUUID());
        insertConnection(seeded.widgetConnection(), userId, seeded.widgetItem());
        insertConnection(seeded.meuPluggyConnection(), userId, seeded.meuPluggyItem());
        jdbc.update("""
                insert into accounts (id, connection_id, user_id, provider_account_id, kind, currency_code, balance,
                                      created_at, updated_at)
                values (?, ?, ?, ?, 'CHECKING', 'BRL', 100.00, ?, ?)
                """, seeded.account(), seeded.widgetConnection(), userId, "acc-" + UUID.randomUUID(), now, now);
        jdbc.update("""
                insert into transactions (id, account_id, user_id, provider_transaction_id, booked_on, amount, direction,
                                          status, created_at, updated_at)
                values (?, ?, ?, ?, ?, 10.00, 'OUTFLOW', 'POSTED', ?, ?)
                """, UUID.randomUUID(), seeded.account(), userId, "tx-" + UUID.randomUUID(), day, now, now);
        jdbc.update("insert into balance_snapshots (account_id, snapshot_date, balance) values (?, ?, 100.00)",
                seeded.account(), day);
        jdbc.update("""
                insert into credit_card_bills (id, account_id, provider_bill_id, due_date, total_amount, currency_code,
                                               created_at, updated_at)
                values (?, ?, ?, ?, 50.00, 'BRL', ?, ?)
                """, UUID.randomUUID(), seeded.account(), "bill-" + UUID.randomUUID(), day, now, now);
        jdbc.update("""
                insert into investments (id, connection_id, user_id, provider_investment_id, kind, currency_code, balance,
                                         created_at, updated_at)
                values (?, ?, ?, ?, 'FIXED_INCOME', 'BRL', 1000.00, ?, ?)
                """, seeded.investment(), seeded.meuPluggyConnection(), userId, "inv-" + UUID.randomUUID(), now, now);
        jdbc.update("insert into investment_snapshots (investment_id, snapshot_date, balance) values (?, ?, 1000.00)",
                seeded.investment(), day);
        jdbc.update("""
                insert into sync_runs (id, connection_id, trigger, status, started_at)
                values (?, ?, 'MANUAL', 'SUCCEEDED', ?)
                """, UUID.randomUUID(), seeded.widgetConnection(), now);
        jdbc.update("""
                insert into fixed_expenses (id, user_id, name, amount, day_of_month, created_at, updated_at)
                values (?, ?, 'Aluguel', 1500.00, 10, ?, ?)
                """, UUID.randomUUID(), userId, now, now);
        jdbc.update("""
                insert into planning_settings (user_id, expected_salary, created_at, updated_at)
                values (?, 5000.00, ?, ?)
                """, userId, now, now);
        jdbc.update("""
                insert into dismissed_fixed_expense_suggestions (id, user_id, description_key, day_of_month, created_at)
                values (?, ?, ?, 5, ?)
                """, UUID.randomUUID(), userId, "0123456789abcdef0123456789abcdef", now);
        jdbc.update("""
                insert into provider_calls (id, called_at, provider, operation, reason, user_id, connection_id,
                                            provider_item_id, outcome, duration_ms)
                values (?, ?, 'PLUGGY', 'FIND_ITEM', 'LINK', ?, ?, ?, 'SUCCESS', 5)
                """, UUID.randomUUID(), now, userId, seeded.widgetConnection(), seeded.widgetItem());
        return seeded;
    }

    private void insertConnection(UUID id, UUID userId, String itemId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                insert into connections (id, user_id, provider, provider_item_id, status, created_at, updated_at)
                values (?, ?, 'PLUGGY', ?, 'NEEDS_ATTENTION', ?, ?)
                """, id, userId, itemId, now, now);
    }

    /** The user's rows in every table seeded, found by the ids seeded (the joins go with the cascade). */
    private Map<String, Integer> rowsOf(UUID userId, Seeded seeded) {
        Map<String, Integer> rows = new LinkedHashMap<>();
        rows.put("users", count("select count(*) from users where id = ?", userId));
        rows.put("connections", count("select count(*) from connections where id in (?, ?)",
                seeded.widgetConnection(), seeded.meuPluggyConnection()));
        rows.put("accounts", count("select count(*) from accounts where id = ?", seeded.account()));
        rows.put("transactions", count("select count(*) from transactions where account_id = ?", seeded.account()));
        rows.put("balance_snapshots", count("select count(*) from balance_snapshots where account_id = ?", seeded.account()));
        rows.put("credit_card_bills", count("select count(*) from credit_card_bills where account_id = ?", seeded.account()));
        rows.put("investments", count("select count(*) from investments where id = ?", seeded.investment()));
        rows.put("investment_snapshots", count("select count(*) from investment_snapshots where investment_id = ?",
                seeded.investment()));
        rows.put("sync_runs", count("select count(*) from sync_runs where connection_id = ?", seeded.widgetConnection()));
        rows.put("fixed_expenses", count("select count(*) from fixed_expenses where user_id = ?", userId));
        rows.put("planning_settings", count("select count(*) from planning_settings where user_id = ?", userId));
        rows.put("dismissed_fixed_expense_suggestions",
                count("select count(*) from dismissed_fixed_expense_suggestions where user_id = ?", userId));
        return rows;
    }

    private int count(String sql, Object... arguments) {
        Integer count = jdbc.queryForObject(sql, Integer.class, arguments);
        return isNull(count) ? 0 : count;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private record Registered(UUID id, String email) {
    }

    private record Seeded(UUID widgetConnection, UUID meuPluggyConnection, UUID account, UUID investment,
                          String widgetItem, String meuPluggyItem) {
    }
}
