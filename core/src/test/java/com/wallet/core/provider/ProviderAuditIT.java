package com.wallet.core.provider;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.connection.ConnectionStatus;
import com.wallet.core.connection.entity.Connection;
import com.wallet.core.connection.repository.ConnectionRepository;
import com.wallet.core.provider.entity.ProviderCall;
import com.wallet.core.provider.mapper.ProviderCallMapper;
import com.wallet.core.provider.repository.ProviderCallRepository;
import com.wallet.core.provider.service.ProviderCallRetention;
import com.wallet.core.sync.SyncRunStatus;
import com.wallet.core.sync.SyncTrigger;
import com.wallet.core.sync.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.wallet.core.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The audit trail end to end: the real Pluggy adapter against WireMock, Postgres in Docker, and the
 * production connection mode, where linking checks the item's owner and unlinking revokes it.
 *
 * <p>The apiKey stays cached for two hours, so only the first test to run sees an {@code AUTH}
 * call. The queries leave it out; {@code PluggyAuditInterceptorTest} covers it.
 */
@SpringBootTest(properties = "wallet.provider.connection-mode=PLUGGY_CONNECT")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ProviderAuditIT {

    private static final String API_KEY = "it-api-key";
    private static final String CLIENT_SECRET = "it-client-secret";
    private static final String EMPTY_PAGE = "{\"page\":1,\"total\":0,\"totalPages\":1,\"results\":[]}";

    @RegisterExtension
    static WireMockExtension pluggy = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void pluggyAtWireMock(DynamicPropertyRegistry registry) {
        registry.add("wallet.pluggy.base-url", pluggy::baseUrl);
        registry.add("wallet.pluggy.client-id", () -> "it-client-id");
        registry.add("wallet.pluggy.client-secret", () -> CLIENT_SECRET);
    }

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SyncService syncService;
    @Autowired private ConnectionRepository connections;
    @Autowired private ProviderCallRepository callRepository;
    @Autowired private ProviderCallRetention retention;
    @Autowired private Clock clock;

    private MockMvc mockMvc;
    private UUID userId;
    private String itemId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userId = TestUsers.register(mockMvc);
        itemId = UUID.randomUUID().toString();
        pluggy.stubFor(WireMock.post("/auth").willReturn(okJson("{\"apiKey\":\"" + API_KEY + "\"}")));
        pluggy.stubFor(get(urlEqualTo("/items/" + itemId)).willReturn(okJson(item(userId))));
        pluggy.stubFor(get(urlPathEqualTo("/accounts")).willReturn(okJson(fixture("accounts.json"))));
        pluggy.stubFor(get(urlPathEqualTo("/v2/transactions")).willReturn(okJson("{\"results\":[],\"next\":null}")));
        pluggy.stubFor(get(urlPathEqualTo("/bills")).willReturn(okJson(EMPTY_PAGE)));
        pluggy.stubFor(get(urlPathEqualTo("/investments")).willReturn(okJson(EMPTY_PAGE)));
        pluggy.stubFor(WireMock.delete(urlEqualTo("/items/" + itemId)).willReturn(aResponse().withStatus(200)));
    }

    @Test
    void linkFirstSyncAndUnlinkAreRecordedWithUserConnectionAndReason(CapturedOutput output) throws Exception {
        UUID connectionId = linked();
        awaitFirstSync(connectionId);
        mockMvc.perform(delete("/internal/connections/" + connectionId).with(as(userId)))
                .andExpect(status().isNoContent());

        List<Call> calls = callsOf(userId);
        assertThat(calls).extracting(Call::reason, Call::operation, Call::connectionId).containsExactlyInAnyOrder(
                tuple("LINK", "FIND_ITEM", null),
                tuple("INITIAL_SYNC", "FIND_ITEM", connectionId),
                tuple("INITIAL_SYNC", "LIST_ACCOUNTS", connectionId),
                tuple("INITIAL_SYNC", "LIST_TRANSACTIONS", connectionId),
                tuple("INITIAL_SYNC", "LIST_TRANSACTIONS", connectionId),
                tuple("INITIAL_SYNC", "LIST_BILLS", connectionId),
                tuple("INITIAL_SYNC", "LIST_INVESTMENTS", connectionId),
                tuple("UNLINK", "DELETE_ITEM", connectionId));
        assertThat(calls).allSatisfy(call -> {
            assertThat(call.itemId()).isEqualTo(itemId);
            assertThat(call.outcome()).isEqualTo("SUCCESS");
            assertThat(call.httpStatus()).isEqualTo(200);
            assertThat(call.errorCode()).isNull();
            assertThat(call.durationMs()).isNotNegative();
        });
        // The connection and everything synced went with the unlink; the record of its calls did not.
        assertThat(connections.findById(connectionId)).isEmpty();

        List<String> lines = output.getAll().lines().filter(line -> line.contains("[PROVIDER_CALL]")).toList();
        assertThat(lines).anyMatch(line -> line.contains("operation=DELETE_ITEM outcome=SUCCESS status=200")
                && line.contains("reason=UNLINK userId=" + userId + " connectionId=" + connectionId + " itemId=" + itemId));
        assertThat(lines).noneMatch(line -> line.contains(API_KEY) || line.contains(CLIENT_SECRET));
    }

    @Test
    void aFailedCallIsRecordedWithItsStatus() {
        pluggy.stubFor(get(urlPathEqualTo("/accounts")).willReturn(serviceUnavailable()));
        UUID connectionId = saveConnection();

        assertThat(syncService.syncNow(connectionId, SyncTrigger.MANUAL)).isEqualTo(SyncRunStatus.FAILED);

        assertThat(callsOf(userId))
                .extracting(Call::reason, Call::operation, Call::connectionId, Call::outcome, Call::httpStatus, Call::errorCode)
                .containsExactlyInAnyOrder(
                        tuple("MANUAL_SYNC", "FIND_ITEM", connectionId, "SUCCESS", 200, null),
                        tuple("MANUAL_SYNC", "LIST_ACCOUNTS", connectionId, "FAILURE", 503, ProviderErrors.UNAVAILABLE));
    }

    /** Linking calls Pluggy inside its transaction; the record is written in its own and survives the rollback. */
    @Test
    void aLinkThatFailsKeepsTheRecordOfItsCall() throws Exception {
        pluggy.stubFor(get(urlEqualTo("/items/" + itemId)).willReturn(notFound()));

        link().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("connection.item_not_found"));

        assertThat(callsOf(userId))
                .extracting(Call::reason, Call::operation, Call::connectionId, Call::itemId, Call::outcome,
                        Call::httpStatus, Call::errorCode)
                .containsExactly(tuple("LINK", "FIND_ITEM", null, itemId, "FAILURE", 404, ProviderErrors.NOT_FOUND));
    }

    @Test
    void aFailingAuditInsertBreaksNeitherTheLinkNorTheSync(CapturedOutput output) throws Exception {
        jdbc.execute("ALTER TABLE provider_calls ADD CONSTRAINT provider_calls_down CHECK (false) NOT VALID");
        try {
            UUID connectionId = linked();
            assertThat(awaitFirstSync(connectionId)).isEqualTo(SyncRunStatus.SUCCEEDED.name());
        } finally {
            jdbc.execute("ALTER TABLE provider_calls DROP CONSTRAINT provider_calls_down");
        }

        assertThat(callsOf(userId)).isEmpty();
        assertThat(output.getAll())
                .contains("Could not store the record of PLUGGY FIND_ITEM")
                .contains("Could not store the record of PLUGGY LIST_INVESTMENTS");
    }

    @Test
    void recordsOlderThanTheRetentionAreDeleted() {
        Instant now = clock.instant();
        ProviderCall expired = ProviderCallMapper.toEntity("PLUGGY", "FIND_ITEM", null, now.minus(Duration.ofDays(181)), 40, 200, null);
        ProviderCall kept = ProviderCallMapper.toEntity("PLUGGY", "FIND_ITEM", null, now.minus(Duration.ofDays(179)), 40, 200, null);
        callRepository.insert(expired);
        callRepository.insert(kept);

        assertThat(retention.deleteExpired()).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForList("select id from provider_calls where id in (?, ?)", UUID.class, expired.id(), kept.id()))
                .containsExactly(kept.id());
    }

    private record Call(String reason, String operation, UUID connectionId, String itemId, String outcome,
                        Integer httpStatus, String errorCode, int durationMs) {
    }

    /** Every call recorded for this user except the apiKey request (see the class comment). */
    private List<Call> callsOf(UUID user) {
        return jdbc.query("""
                select reason, operation, connection_id, provider_item_id, outcome, http_status, error_code, duration_ms
                  from provider_calls
                 where user_id = ? and operation <> 'AUTH'
                """, (row, index) -> new Call(row.getString("reason"), row.getString("operation"),
                        row.getObject("connection_id", UUID.class), row.getString("provider_item_id"),
                        row.getString("outcome"), row.getObject("http_status", Integer.class),
                        row.getString("error_code"), row.getInt("duration_ms")),
                user);
    }

    private ResultActions link() throws Exception {
        return mockMvc.perform(post("/internal/connections").with(as(userId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerItemId\":\"%s\"}".formatted(itemId)));
    }

    private UUID linked() throws Exception {
        String body = link().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID saveConnection() {
        Instant now = clock.instant();
        return connections.save(Connection.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .provider("PLUGGY")
                .providerItemId(itemId)
                .status(ConnectionStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build()).getId();
    }

    /** The first sync runs in the background after linking; ten seconds is only a safety net. */
    private String awaitFirstSync(UUID connectionId) throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            List<String> finished = jdbc.queryForList(
                    "select status from sync_runs where connection_id = ? and status <> 'RUNNING'", String.class, connectionId);
            if (!finished.isEmpty()) {
                return finished.getFirst();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The first sync of connection " + connectionId + " did not finish");
    }

    /** An item created for {@code owner} through Pluggy Connect. */
    private String item(UUID owner) {
        return """
                {"id":"%s","connector":{"id":212,"name":"Nubank","imageUrl":"https://cdn.pluggy.ai/assets/connector-icons/212.svg"},
                 "status":"UPDATED","lastUpdatedAt":"2026-10-18T09:00:00.000Z","clientUserId":"%s"}
                """.formatted(itemId, owner);
    }

    private static String fixture(String name) {
        try (InputStream stream = ProviderAuditIT.class.getResourceAsStream("/pluggy/" + name)) {
            if (stream == null) {
                throw new IllegalStateException("Missing fixture " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
