package com.wallet.core.planning;

import com.wallet.core.TestMockMvc;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.planning.service.FixedExpenseNameBackfill;
import com.wallet.core.shared.crypto.DataCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static com.wallet.core.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The name of a fixed expense is encrypted at rest (L7) and read back whole; rows written in plain
 * text before are readable at once and encrypted, once, by the start-up backfill.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FixedExpenseEncryptionIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataCipher cipher;

    @Autowired
    private FixedExpenseNameBackfill backfill;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = TestMockMvc.of(context);
        userId = TestUsers.register(mockMvc);
    }

    @Test
    void theNameIsEncryptedInTheDatabaseAndReadableThroughTheApi() throws Exception {
        String id = create("Psicólogo", 10);

        String stored = jdbc.queryForObject("select name from fixed_expenses where id = ?::uuid", String.class, id);
        assertThat(stored).startsWith("v1:").doesNotContain("Psic");
        assertThat(cipher.decrypt(stored)).isEqualTo("Psicólogo");
        read().andExpect(jsonPath("$[*].name", contains("Psicólogo")));
        mockMvc.perform(get("/internal/planning/month").with(as(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixedExpenses[*].name", contains("Psicólogo")));
    }

    /** The database can no longer sort by name, so the list does it after decrypting: day first, then name, accents and case aside. */
    @Test
    void withinADayTheListIsStillByName() throws Exception {
        create("cadeira", 5);
        create("banana", 5);
        create("Água", 5);
        create("Zebra", 1);

        read().andExpect(jsonPath("$[*].name", contains("Zebra", "Água", "banana", "cadeira")));
    }

    @Test
    void aRowWrittenInPlainTextReadsAsItIsAndTheBackfillEncryptsOnlyThat() throws Exception {
        UUID legacy = insertLegacy("Aluguel", 7);
        String alreadyEncrypted = create("Escola", 8);
        String escolaBefore = storedName(alreadyEncrypted);
        assertThat(storedName(legacy.toString())).isEqualTo("Aluguel");
        read().andExpect(jsonPath("$[*].name", contains("Aluguel", "Escola")));

        int encrypted = backfill.run();

        assertThat(encrypted).isGreaterThanOrEqualTo(1);
        assertThat(storedName(legacy.toString())).startsWith("v1:").doesNotContain("Aluguel");
        assertThat(cipher.decrypt(storedName(legacy.toString()))).isEqualTo("Aluguel");
        // What was already encrypted is untouched, byte for byte.
        assertThat(storedName(alreadyEncrypted)).isEqualTo(escolaBefore);
        read().andExpect(jsonPath("$[*].name", contains("Aluguel", "Escola")));
    }

    @Test
    void runningTheBackfillAgainChangesNothing() throws Exception {
        UUID legacy = insertLegacy("Condomínio", 3);
        backfill.run();
        String once = storedName(legacy.toString());

        assertThat(backfill.run()).isZero();

        assertThat(storedName(legacy.toString())).isEqualTo(once);
        assertThat(cipher.decrypt(once)).isEqualTo("Condomínio");
    }

    @Test
    void anUpdateKeepsTheNameEncrypted() throws Exception {
        UUID legacy = insertLegacy("Aluguel", 7);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/internal/planning/fixed-expenses/" + legacy).with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aluguel novo\",\"amount\":\"1600.00\",\"dayOfMonth\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aluguel novo"));

        assertThat(cipher.decrypt(storedName(legacy.toString()))).isEqualTo("Aluguel novo");
    }

    private String create(String name, int dayOfMonth) throws Exception {
        String body = mockMvc.perform(post("/internal/planning/fixed-expenses").with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"amount\":\"100.00\",\"dayOfMonth\":%d}".formatted(name, dayOfMonth)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private ResultActions read() throws Exception {
        return mockMvc.perform(get("/internal/planning/fixed-expenses").with(as(userId))).andExpect(status().isOk());
    }

    /** A row as it was before the encryption: the name straight in the column. */
    private UUID insertLegacy(String name, int dayOfMonth) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                insert into fixed_expenses (id, user_id, name, amount, day_of_month, created_at, updated_at)
                values (?, ?, ?, 1500.00, ?, ?, ?)
                """, id, userId, name, dayOfMonth, now, now);
        return id;
    }

    private String storedName(String id) {
        return jdbc.queryForObject("select name from fixed_expenses where id = ?::uuid", String.class, id);
    }
}
