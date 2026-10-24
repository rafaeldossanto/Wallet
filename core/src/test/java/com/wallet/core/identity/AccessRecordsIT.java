package com.wallet.core.identity;

import com.jayway.jsonpath.JsonPath;
import com.wallet.core.MutableClock;
import com.wallet.core.TestClockConfiguration;
import com.wallet.core.TestMockMvc;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.identity.controller.ClientAddress;
import com.wallet.core.identity.service.AccountHousekeeping;
import com.wallet.core.shared.crypto.DataCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.wallet.core.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The records of access the Marco Civil da Internet asks for (art. 15): one per successful login and
 * per refresh, the address encrypted, kept after the account is gone, deleted after 6 months.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class AccessRecordsIT {

    private static final String LOGIN_ADDRESS = "203.0.113.5";
    private static final String REFRESH_ADDRESS = "198.51.100.9";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataCipher cipher;

    @Autowired
    private MutableClock clock;

    @Autowired
    private AccountHousekeeping housekeeping;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clock.set(TestClockConfiguration.START);
        mockMvc = TestMockMvc.of(context);
    }

    @Test
    void aLoginAndARefreshEachLeaveARecordWithTheAddressEncrypted() throws Exception {
        String email = "access+" + UUID.randomUUID() + "@example.com";
        UUID userId = TestUsers.register(mockMvc, email);

        String body = login(email, LOGIN_ADDRESS).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        clock.advance(Duration.ofMinutes(5));
        refresh(JsonPath.read(body, "$.refreshToken"), REFRESH_ADDRESS).andExpect(status().isOk());

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select event, ip, occurred_at from access_records where user_id = ? order by occurred_at", userId);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("event", "LOGIN");
        assertThat(rows.get(1)).containsEntry("event", "REFRESH");
        assertThat(((java.sql.Timestamp) rows.get(0).get("occurred_at")).toInstant()).isEqualTo(TestClockConfiguration.START);
        assertThat(((java.sql.Timestamp) rows.get(1).get("occurred_at")).toInstant())
                .isEqualTo(TestClockConfiguration.START.plus(Duration.ofMinutes(5)));
        // What is in the database is not the address, and the key opens it.
        assertThat((String) rows.get(0).get("ip")).startsWith("v1:").doesNotContain(LOGIN_ADDRESS);
        assertThat((String) rows.get(1).get("ip")).startsWith("v1:").doesNotContain(REFRESH_ADDRESS);
        assertThat(cipher.decrypt((String) rows.get(0).get("ip"))).isEqualTo(LOGIN_ADDRESS);
        assertThat(cipher.decrypt((String) rows.get(1).get("ip"))).isEqualTo(REFRESH_ADDRESS);
    }

    @Test
    void aFailedLoginOrARefusedRefreshLeavesNothing() throws Exception {
        String email = "access+" + UUID.randomUUID() + "@example.com";
        UUID userId = TestUsers.register(mockMvc, email);

        login(email, LOGIN_ADDRESS, "not-the-password").andExpect(status().isUnauthorized());
        refresh("not-a-refresh-token", REFRESH_ADDRESS).andExpect(status().isUnauthorized());

        assertThat(records(userId)).isZero();
    }

    /** Without the BFF's header (a test calling the core directly) the record still exists, as "unknown". */
    @Test
    void withoutTheAddressHeaderTheRecordSaysUnknown() throws Exception {
        String email = "access+" + UUID.randomUUID() + "@example.com";
        UUID userId = TestUsers.register(mockMvc, email);

        mockMvc.perform(post("/internal/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, TestUsers.PASSWORD)))
                .andExpect(status().isOk());

        String ip = jdbc.queryForObject("select ip from access_records where user_id = ?", String.class, userId);
        assertThat(cipher.decrypt(ip)).isEqualTo("unknown");
    }

    /** The law keeps them 6 months whatever happens to the account: no foreign key, no cascade (LGPD, art. 16, I). */
    @Test
    void deletingTheAccountKeepsItsRecordsOfAccess() throws Exception {
        String email = "access+" + UUID.randomUUID() + "@example.com";
        UUID userId = TestUsers.register(mockMvc, email);
        login(email, LOGIN_ADDRESS).andExpect(status().isOk());
        assertThat(records(userId)).isOne();

        mockMvc.perform(delete("/internal/me").with(as(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(TestUsers.PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, userId)).isZero();
        assertThat(records(userId)).isOne();
    }

    @Test
    void theScheduledRunDeletesRecordsOlderThanSixMonthsAndKeepsTheRest(CapturedOutput output) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID older = record(userId, 200);
        UUID alsoOlder = record(userId, 400);
        UUID withinSixMonths = record(userId, 180);
        UUID recent = record(userId, 1);

        runTheScheduledTask();

        assertThat(exists(older)).isFalse();
        assertThat(exists(alsoOlder)).isFalse();
        assertThat(exists(withinSixMonths)).isTrue();
        assertThat(exists(recent)).isTrue();
        assertThat(output.getAll()).contains("[RETENTION] Identity deleted 2 records of access older than 185 days");
        // Counts only: no address, no id of a person, in the log.
        assertThat(output.getAll()).doesNotContain(userId.toString());
    }

    private void runTheScheduledTask() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        housekeeping.configureTasks(registrar);
        assertThat(registrar.getCronTaskList()).hasSize(1);
        registrar.getCronTaskList().getFirst().getRunnable().run();
    }

    private ResultActions login(String email, String address) throws Exception {
        return login(email, address, TestUsers.PASSWORD);
    }

    private ResultActions login(String email, String address, String password) throws Exception {
        return mockMvc.perform(post("/internal/auth/login").header(ClientAddress.HEADER, address)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private ResultActions refresh(String refreshToken, String address) throws Exception {
        return mockMvc.perform(post("/internal/auth/refresh").header(ClientAddress.HEADER, address)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private UUID record(UUID userId, int daysAgo) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into access_records (id, user_id, event, ip, occurred_at) values (?, ?, 'LOGIN', ?, ?)",
                id, userId, cipher.encrypt("203.0.113.77"),
                Timestamp.from(TestClockConfiguration.START.minus(Duration.ofDays(daysAgo))));
        return id;
    }

    private int records(UUID userId) {
        return jdbc.queryForObject("select count(*) from access_records where user_id = ?", Integer.class, userId);
    }

    private boolean exists(UUID recordId) {
        return jdbc.queryForObject("select count(*) from access_records where id = ?", Integer.class, recordId) > 0;
    }
}
