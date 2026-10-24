package com.wallet.core.connection;

import com.jayway.jsonpath.JsonPath;
import com.wallet.core.MutableClock;
import com.wallet.core.TestClockConfiguration;
import com.wallet.core.TestMockMvc;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.provider.FinancialDataProvider;
import com.wallet.core.provider.ProviderItem;
import com.wallet.core.provider.ProviderItemStatus;
import com.wallet.core.shared.web.Bulkhead;
import com.wallet.core.shared.web.Bulkheads;
import com.wallet.core.sync.SyncRunStatus;
import com.wallet.core.sync.SyncTrigger;
import com.wallet.core.sync.service.SyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.wallet.core.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The record of what the user agreed to (LGPD L3) and the end of an authorization (L5): one consent
 * row per link and per renewal, its revocation surviving the unlink, the consent version required
 * on the routes that start a connection, and the renewal that puts an expired connection back.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class ConnectionConsentIT {

    private static final String VERSION = "open-finance-v1";
    private static final String SCOPES = "ACCOUNTS,TRANSACTIONS,CREDIT_CARDS,INVESTMENTS";
    private static final String REDIRECT = "com.wallet.wallet://pluggy-connect";
    private static final Instant NOW = TestClockConfiguration.START;
    private static final Instant ENDS = NOW.plus(Duration.ofDays(90));

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SyncService syncService;

    @Autowired
    private Bulkheads bulkheads;

    @MockitoBean
    private FinancialDataProvider provider;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = TestMockMvc.of(context);
        clock.set(NOW);
        userId = TestUsers.register(mockMvc);
    }

    /** Linking starts the first sync in the background; let it end before Mockito resets the provider. */
    @AfterEach
    void awaitFirstSyncs() throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            Integer pending = jdbc.queryForObject("""
                    select count(*) from connections c
                     where c.user_id = ?
                       and not exists (select 1 from sync_runs r where r.connection_id = c.id and r.status <> 'RUNNING')
                    """, Integer.class, userId);
            if (pending == 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The first sync of a connection of user " + userId + " did not finish");
    }

    @Test
    void linkingRecordsWhatTheUserAgreedTo() throws Exception {
        String itemId = stubItem(ENDS);

        UUID connectionId = link(itemId);

        Map<String, Object> consent = onlyConsentOf(connectionId);
        assertThat(consent).containsEntry("user_id", userId)
                .containsEntry("purpose_version", VERSION)
                .containsEntry("data_scopes", SCOPES);
        assertThat(instant(consent, "granted_at")).isEqualTo(NOW);
        assertThat(instant(consent, "expires_at")).isEqualTo(ENDS);
        assertThat(consent.get("revoked_at")).isNull();
    }

    @Test
    void withoutTheConsentVersionNothingIsLinkedOrCalled() throws Exception {
        String itemId = stubItem(ENDS);

        post("/internal/connections", "{\"providerItemId\":\"%s\"}".formatted(itemId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
        post("/internal/connections", "{\"providerItemId\":\"%s\",\"consentVersion\":\"  \"}".formatted(itemId))
                .andExpect(status().isBadRequest());

        verify(provider, never()).findItem(anyString());
        assertThat(jdbc.queryForObject("select count(*) from connections where user_id = ?", Integer.class, userId)).isZero();
    }

    @Test
    void theConnectTokenAlsoNeedsTheConsentVersion() throws Exception {
        post("/internal/connections/connect-token", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
        post("/internal/connections/connect-token", "{\"connectionId\":\"%s\"}".formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest());

        verify(provider, never()).createConnectToken(anyString(), anyString(), any());
    }

    @Test
    void aNewConnectionGetsAConnectTokenWithoutAnItem() throws Exception {
        when(provider.createConnectToken(userId.toString(), REDIRECT, null)).thenReturn("connect-token-1");

        post("/internal/connections/connect-token", consent())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectToken").value("connect-token-1"));
    }

    /** The Pluggy Connect widget in update mode: the token carries the item that is being renewed. */
    @Test
    void renewingGetsAConnectTokenForTheItemOfTheConnection() throws Exception {
        String itemId = stubItem(ENDS);
        UUID connectionId = link(itemId);
        when(provider.createConnectToken(userId.toString(), REDIRECT, itemId)).thenReturn("update-token-1");

        post("/internal/connections/connect-token", "{\"consentVersion\":\"%s\",\"connectionId\":\"%s\"}".formatted(VERSION, connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectToken").value("update-token-1"));
    }

    @Test
    void thereIsNoConnectTokenForAnotherUsersConnection() throws Exception {
        UUID connectionId = link(stubItem(ENDS));
        UUID other = TestUsers.register(mockMvc);

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/connections/connect-token").with(as(other)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentVersion\":\"%s\",\"connectionId\":\"%s\"}".formatted(VERSION, connectionId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("connection.not_found"));

        verify(provider, never()).createConnectToken(anyString(), anyString(), any());
    }

    /** Proof that the user revoked has to outlive the connection: the cascade must not take it away. */
    @Test
    void unlinkingRecordsTheRevocationAndTheRecordSurvivesTheConnection() throws Exception {
        UUID connectionId = link(stubItem(ENDS));
        clock.advance(Duration.ofDays(3));

        mockMvc.perform(delete("/internal/connections/" + connectionId).with(as(userId)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from connections where id = ?", Integer.class, connectionId)).isZero();
        Map<String, Object> consent = onlyConsentOf(connectionId);
        assertThat(instant(consent, "granted_at")).isEqualTo(NOW);
        assertThat(instant(consent, "revoked_at")).isEqualTo(NOW.plus(Duration.ofDays(3)));
    }

    @Test
    void deletingTheAccountTakesTheConsentRecordsWithIt() throws Exception {
        UUID connectionId = link(stubItem(ENDS));

        jdbc.update("delete from users where id = ?", userId);

        assertThat(jdbc.queryForObject("select count(*) from connection_consents where connection_id = ?", Integer.class, connectionId))
                .isZero();
    }

    @Test
    void renewingPutsAnExpiredConnectionBackAndRecordsTheNewConsent() throws Exception {
        String itemId = stubItem(ENDS);
        UUID connectionId = link(itemId);
        awaitFirstSyncs();
        clock.advance(Duration.ofDays(91));
        // The poll finds the date passed: it marks the connection and leaves a SKIPPED run, which must not count as a recent read.
        assertThat(syncService.syncNow(connectionId, SyncTrigger.SCHEDULED)).isEqualTo(SyncRunStatus.SKIPPED);
        assertThat(jdbc.queryForObject("select status from connections where id = ?", String.class, connectionId))
                .isEqualTo("CONSENT_EXPIRED");
        Instant newEnds = clock.instant().plus(Duration.ofDays(90));
        stubItem(itemId, newEnds);

        post("/internal/connections/" + connectionId + "/renew", consent())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.consentExpiresAt").value(newEnds.toString()));

        List<Map<String, Object>> consents = jdbc.queryForList(
                "select * from connection_consents where connection_id = ? order by granted_at", connectionId);
        assertThat(consents).hasSize(2);
        assertThat(instant(consents.get(1), "granted_at")).isEqualTo(clock.instant());
        assertThat(instant(consents.get(1), "expires_at")).isEqualTo(newEnds);
        assertThat(consents.get(1)).containsEntry("purpose_version", VERSION).containsEntry("data_scopes", SCOPES);
        awaitFinishedSyncs(connectionId, 3);
        assertThat(jdbc.queryForObject("select count(*) from sync_runs where connection_id = ? and trigger = 'MANUAL' and status = 'SUCCEEDED'",
                Integer.class, connectionId)).isEqualTo(1);
    }

    /** The sync that follows a renewal is the user's update: the manual cooldown applies, and it is not skipped silently. */
    @Test
    void theSyncAfterARenewalWaitsOutTheCooldownOfTheManualUpdate() throws Exception {
        String itemId = stubItem(ENDS);
        UUID connectionId = link(itemId);
        awaitFirstSyncs();
        // The first read ended a moment ago: no clock movement, so the cooldown is still running.
        jdbc.update("update connections set status = 'CONSENT_EXPIRED' where id = ?", connectionId);
        stubItem(itemId, ENDS.plus(Duration.ofDays(90)));

        post("/internal/connections/" + connectionId + "/renew", consent())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Thread.sleep(500);
        assertThat(jdbc.queryForObject("select count(*) from sync_runs where connection_id = ?", Integer.class, connectionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from connection_consents where connection_id = ?", Integer.class, connectionId))
                .isEqualTo(2);
    }

    /** ...and so does the bulkhead of the manual updates: a user with their share running does not get one more. */
    @Test
    void theSyncAfterARenewalRespectsTheManualBulkhead() throws Exception {
        String itemId = stubItem(ENDS);
        UUID connectionId = link(itemId);
        awaitFirstSyncs();
        clock.advance(Duration.ofDays(91));
        jdbc.update("update connections set status = 'CONSENT_EXPIRED' where id = ?", connectionId);
        stubItem(itemId, clock.instant().plus(Duration.ofDays(90)));
        Bulkhead.Permit first = bulkheads.manualSync().enter(userId);
        Bulkhead.Permit second = bulkheads.manualSync().enter(userId);
        try {
            post("/internal/connections/" + connectionId + "/renew", consent())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));

            Thread.sleep(500);
            assertThat(jdbc.queryForObject("select count(*) from sync_runs where connection_id = ?", Integer.class, connectionId))
                    .isEqualTo(1);
        } finally {
            first.close();
            second.close();
        }
    }

    /** The widget was closed without renewing: Pluggy still says the old date, and nothing changes. */
    @Test
    void renewingWhileTheProviderStillSaysTheOldDateIs409() throws Exception {
        String itemId = stubItem(ENDS);
        UUID connectionId = link(itemId);
        awaitFirstSyncs();
        clock.advance(Duration.ofDays(91));
        jdbc.update("update connections set status = 'CONSENT_EXPIRED' where id = ?", connectionId);

        post("/internal/connections/" + connectionId + "/renew", consent())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("connection.consent_not_renewed"));

        assertThat(jdbc.queryForObject("select status from connections where id = ?", String.class, connectionId))
                .isEqualTo("CONSENT_EXPIRED");
        assertThat(jdbc.queryForObject("select count(*) from connection_consents where connection_id = ?", Integer.class, connectionId))
                .isEqualTo(1);
    }

    @Test
    void renewingNeedsTheConsentVersionAndTheOwner() throws Exception {
        UUID connectionId = link(stubItem(ENDS));
        awaitFirstSyncs();
        UUID other = TestUsers.register(mockMvc);

        post("/internal/connections/" + connectionId + "/renew", "{}").andExpect(status().isBadRequest());
        mockMvc.perform(MockMvcRequestBuilders.post("/internal/connections/" + connectionId + "/renew").with(as(other))
                        .contentType(MediaType.APPLICATION_JSON).content(consent()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("connection.not_found"));
    }

    @Test
    void theListShowsTheExpiredStatusAndTheDate() throws Exception {
        UUID connectionId = link(stubItem(ENDS));
        awaitFirstSyncs();
        jdbc.update("update connections set status = 'CONSENT_EXPIRED' where id = ?", connectionId);

        mockMvc.perform(get("/internal/connections").with(as(userId)))
                .andExpect(jsonPath("$[0].status").value("CONSENT_EXPIRED"))
                .andExpect(jsonPath("$[0].consentExpiresAt").value(ENDS.toString()));
    }

    /** A renewal starts another sync in the background; let it end before Mockito resets the provider. */
    private void awaitFinishedSyncs(UUID connectionId, int count) throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            Integer finished = jdbc.queryForObject(
                    "select count(*) from sync_runs where connection_id = ? and status <> 'RUNNING'", Integer.class, connectionId);
            if (finished >= count) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Expected " + count + " finished syncs of connection " + connectionId);
    }

    private ResultActions post(String path, String body) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post(path)
                .with(as(userId)).contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(request);
    }

    private UUID link(String itemId) throws Exception {
        String body = post("/internal/connections", "{\"providerItemId\":\"%s\",\"consentVersion\":\"%s\"}".formatted(itemId, VERSION))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static String consent() {
        return "{\"consentVersion\":\"%s\"}".formatted(VERSION);
    }

    private String stubItem(Instant consentExpiresAt) {
        return stubItem(UUID.randomUUID().toString(), consentExpiresAt);
    }

    private String stubItem(String itemId, Instant consentExpiresAt) {
        clearInvocations(provider);
        when(provider.findItem(itemId)).thenReturn(new ProviderItem(itemId, "Nubank", null, ProviderItemStatus.READY,
                NOW, consentExpiresAt, null));
        return itemId;
    }

    private Map<String, Object> onlyConsentOf(UUID connectionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from connection_consents where connection_id = ?", connectionId);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private static Instant instant(Map<String, Object> row, String column) {
        return ((Timestamp) row.get(column)).toInstant();
    }
}
