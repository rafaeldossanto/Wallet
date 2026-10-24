package com.wallet.core.privacy;

import com.jayway.jsonpath.JsonPath;
import com.wallet.core.TestMockMvc;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.shared.crypto.DataCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.wallet.core.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The export of a user's data (LGPD, art. 18, II and V): rows from every module, descriptions
 * decrypted, nothing that signs in, and nothing of another user.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DataExportIT {

    private static final String SECRET = "JBSWY3DPEHPK3PXP";
    private static final Timestamp NOW = Timestamp.from(Instant.parse("2026-10-01T12:00:00Z"));

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataCipher cipher;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = TestMockMvc.of(context);
    }

    @Test
    void exportsWhatEveryModuleHoldsWithDecryptedDescriptions() throws Exception {
        String email = "export+" + UUID.randomUUID() + "@example.com";
        UUID userId = TestUsers.register(mockMvc, email);
        Seeded mine = seed(userId, "Supermercado Zé");

        String body = mockMvc.perform(get("/internal/me/export").with(as(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.exportedAt").isString())
                .andExpect(jsonPath("$.profile.id").value(userId.toString()))
                .andExpect(jsonPath("$.profile.email").value(email))
                .andExpect(jsonPath("$.profile.displayName").value("Test"))
                .andExpect(jsonPath("$.profile.twoFactorEnabled").value(true))
                .andExpect(jsonPath("$.profile.emailConfirmedAt").isString())
                .andExpect(jsonPath("$.connections.length()").value(1))
                .andExpect(jsonPath("$.connections[0].id").value(mine.connection().toString()))
                .andExpect(jsonPath("$.connections[0].institutionName").value("Banco de Teste"))
                .andExpect(jsonPath("$.connections[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.connections[0].consentExpiresAt").value("2027-01-15T12:00:00Z"))
                .andExpect(jsonPath("$.banking.accounts.length()").value(2))
                .andExpect(jsonPath("$.banking.accounts[0].name").value("Conta corrente"))
                .andExpect(jsonPath("$.banking.accounts[0].numberLastDigits").value("1234"))
                .andExpect(jsonPath("$.banking.accounts[0].balance").value("2500.50"))
                .andExpect(jsonPath("$.banking.accounts[1].creditLimit").value("8000.00"))
                .andExpect(jsonPath("$.banking.balances.length()").value(1))
                .andExpect(jsonPath("$.banking.balances[0].balance").value("2500.50"))
                .andExpect(jsonPath("$.banking.transactions.length()").value(2))
                .andExpect(jsonPath("$.banking.bills.length()").value(1))
                .andExpect(jsonPath("$.banking.bills[0].total").value("640.10"))
                .andExpect(jsonPath("$.investments.positions.length()").value(2))
                .andExpect(jsonPath("$.investments.balances.length()").value(1))
                .andExpect(jsonPath("$.planning.expectedSalary").value("5200.00"))
                .andExpect(jsonPath("$.planning.fixedExpenses.length()").value(1))
                .andExpect(jsonPath("$.planning.fixedExpenses[0].name").value("Aluguel"))
                .andExpect(jsonPath("$.planning.fixedExpenses[0].amount").value("1500.00"))
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> transactions = JsonPath.read(body, "$.banking.transactions");
        assertThat(transactions).extracting(row -> row.get("description"))
                .containsExactly("Supermercado Zé", "Pagamento Farmácia São João");
        assertThat(transactions.get(0)).containsEntry("amount", "123.45").containsEntry("direction", "OUTFLOW")
                .containsEntry("installmentNumber", 3).containsEntry("installmentTotal", 10);
        List<Map<String, Object>> positions = JsonPath.read(body, "$.investments.positions");
        assertThat(positions).extracting(row -> row.get("name")).containsExactlyInAnyOrder("CDB Aberto", "CDB Encerrado");
        assertThat(positions).filteredOn(row -> "CDB Encerrado".equals(row.get("name")))
                .allSatisfy(row -> assertThat(row.get("closedAt")).isNotNull());
        assertThat(Instant.parse(JsonPath.read(body, "$.exportedAt")))
                .isBetween(Instant.now().minus(Duration.ofMinutes(5)), Instant.now().plus(Duration.ofMinutes(5)));
    }

    /** What signs the user in is never in the file: the hash, the secret, the codes, the tokens. */
    @Test
    void neverHoldsAnythingThatSignsTheUserIn() throws Exception {
        UUID userId = TestUsers.register(mockMvc);
        seed(userId, "Supermercado Zé");
        String passwordHash = jdbc.queryForObject("select password_hash from users where id = ?", String.class, userId);
        String storedSecret = jdbc.queryForObject("select totp_secret from users where id = ?", String.class, userId);

        String body = mockMvc.perform(get("/internal/me/export").with(as(userId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(passwordHash).doesNotContain(storedSecret).doesNotContain(SECRET);
        assertThat(body.toLowerCase()).doesNotContain("password").doesNotContain("totp").doesNotContain("secret")
                .doesNotContain("token").doesNotContain("recovery");
        // The encrypted form of a description is never what the file holds either.
        assertThat(body).doesNotContain("v1:");
    }

    @Test
    void holdsNothingOfAnotherUser() throws Exception {
        UUID mine = TestUsers.register(mockMvc);
        UUID theirs = TestUsers.register(mockMvc);
        seed(mine, "Supermercado Zé");
        Seeded other = seed(theirs, "Descrição só da outra pessoa");

        String body = mockMvc.perform(get("/internal/me/export").with(as(mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banking.transactions.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Descrição só da outra pessoa")
                .doesNotContain(theirs.toString())
                .doesNotContain(other.connection().toString())
                .doesNotContain(other.account().toString())
                .doesNotContain(other.investment().toString());
    }

    @Test
    void aUserWithNothingGetsAnEmptyButCompleteFile() throws Exception {
        UUID userId = TestUsers.register(mockMvc);

        mockMvc.perform(get("/internal/me/export").with(as(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.profile.twoFactorEnabled").value(false))
                .andExpect(jsonPath("$.connections").isEmpty())
                .andExpect(jsonPath("$.banking.accounts").isEmpty())
                .andExpect(jsonPath("$.banking.transactions").isEmpty())
                .andExpect(jsonPath("$.investments.positions").isEmpty())
                .andExpect(jsonPath("$.planning.fixedExpenses").isEmpty());
    }

    @Test
    void needsAnAuthenticatedUserThatStillExists() throws Exception {
        mockMvc.perform(get("/internal/me/export"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/me/export").with(as(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    /**
     * One of everything. The user's second factor is on; one transaction is soft-deleted (the
     * provider stopped reporting it) and must stay out; one position is closed and must stay in.
     */
    private Seeded seed(UUID userId, String description) {
        Date day = Date.valueOf(LocalDate.of(2026, 9, 28));
        Seeded seeded = new Seeded(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        UUID card = UUID.randomUUID();
        jdbc.update("update users set totp_secret = ?, totp_enabled_at = ? where id = ?",
                cipher.encrypt(SECRET), NOW, userId);
        jdbc.update("""
                insert into connections (id, user_id, provider, provider_item_id, institution_name, status,
                                         last_synced_at, consent_expires_at, created_at, updated_at)
                values (?, ?, 'PLUGGY', ?, 'Banco de Teste', 'ACTIVE', ?, ?, ?, ?)
                """, seeded.connection(), userId, "item-" + UUID.randomUUID(), NOW,
                Timestamp.from(Instant.parse("2027-01-15T12:00:00Z")), NOW, NOW);
        jdbc.update("""
                insert into accounts (id, connection_id, user_id, provider_account_id, kind, name, number_last_digits,
                                      currency_code, balance, created_at, updated_at)
                values (?, ?, ?, ?, 'CHECKING', 'Conta corrente', '1234', 'BRL', 2500.50, ?, ?)
                """, seeded.account(), seeded.connection(), userId, "acc-" + UUID.randomUUID(), NOW, NOW);
        jdbc.update("""
                insert into accounts (id, connection_id, user_id, provider_account_id, kind, name, number_last_digits,
                                      currency_code, balance, credit_limit, available_credit, created_at, updated_at)
                values (?, ?, ?, ?, 'CREDIT_CARD', 'Cartão', '9876', 'BRL', 640.10, 8000.00, 7359.90, ?, ?)
                """, card, seeded.connection(), userId, "acc-" + UUID.randomUUID(),
                Timestamp.from(Instant.parse("2026-10-01T12:00:01Z")), NOW);
        insertTransaction(userId, seeded.account(), description, "123.45", day, 3, 10, null);
        insertTransaction(userId, seeded.account(), "Pagamento Farmácia São João", "80.00",
                Date.valueOf(LocalDate.of(2026, 9, 29)), null, null, null);
        insertTransaction(userId, seeded.account(), "Já não existe no banco", "5.00", day, null, null, NOW);
        jdbc.update("insert into balance_snapshots (account_id, snapshot_date, balance) values (?, ?, 2500.50)",
                seeded.account(), day);
        jdbc.update("""
                insert into credit_card_bills (id, account_id, provider_bill_id, due_date, closing_date, total_amount,
                                               minimum_payment, currency_code, created_at, updated_at)
                values (?, ?, ?, ?, ?, 640.10, 100.00, 'BRL', ?, ?)
                """, UUID.randomUUID(), card, "bill-" + UUID.randomUUID(), Date.valueOf(LocalDate.of(2026, 10, 10)),
                Date.valueOf(LocalDate.of(2026, 10, 3)), NOW, NOW);
        jdbc.update("""
                insert into investments (id, connection_id, user_id, provider_investment_id, kind, name, currency_code,
                                         balance, amount_invested, created_at, updated_at)
                values (?, ?, ?, ?, 'FIXED_INCOME', 'CDB Aberto', 'BRL', 1000.00, 900.00, ?, ?)
                """, seeded.investment(), seeded.connection(), userId, "inv-" + UUID.randomUUID(), NOW, NOW);
        jdbc.update("""
                insert into investments (id, connection_id, user_id, provider_investment_id, kind, name, currency_code,
                                         balance, closed_at, created_at, updated_at)
                values (?, ?, ?, ?, 'FIXED_INCOME', 'CDB Encerrado', 'BRL', 0.00, ?, ?, ?)
                """, UUID.randomUUID(), seeded.connection(), userId, "inv-" + UUID.randomUUID(), NOW,
                Timestamp.from(Instant.parse("2026-10-01T12:00:02Z")), NOW);
        jdbc.update("insert into investment_snapshots (investment_id, snapshot_date, balance) values (?, ?, 1000.00)",
                seeded.investment(), day);
        jdbc.update("""
                insert into fixed_expenses (id, user_id, name, amount, day_of_month, created_at, updated_at)
                values (?, ?, 'Aluguel', 1500.00, 10, ?, ?)
                """, UUID.randomUUID(), userId, NOW, NOW);
        jdbc.update("""
                insert into planning_settings (user_id, expected_salary, created_at, updated_at)
                values (?, 5200.00, ?, ?)
                """, userId, NOW, NOW);
        return seeded;
    }

    private void insertTransaction(UUID userId, UUID accountId, String description, String amount, Date day,
                                   Integer installmentNumber, Integer installmentTotal, Timestamp deletedAt) {
        jdbc.update("""
                insert into transactions (id, account_id, user_id, provider_transaction_id, booked_on, description,
                                          amount, direction, status, installment_number, installment_total, deleted_at,
                                          created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?::numeric, 'OUTFLOW', 'POSTED', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), accountId, userId, "tx-" + UUID.randomUUID(), day, cipher.encrypt(description),
                amount, installmentNumber, installmentTotal, deletedAt, NOW, NOW);
    }

    private record Seeded(UUID connection, UUID account, UUID investment) {
    }
}
