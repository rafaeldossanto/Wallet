package com.wallet.core.provider.pluggy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.wallet.core.MutableClock;
import com.wallet.core.provider.ProviderCallContext;
import com.wallet.core.provider.ProviderCallReason;
import com.wallet.core.provider.ProviderErrors;
import com.wallet.core.provider.entity.ProviderCall;
import com.wallet.core.provider.entity.ProviderCallOutcome;
import com.wallet.core.provider.service.ProviderCallRecorder;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.finance.AccountKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The audit interceptor on the real adapter against a fake Pluggy, with an in-memory recorder.
 * Runs without Docker or Spring, so it stays in the unit suite.
 */
class PluggyAuditInterceptorTest {

    private static final String ITEM_ID = "5b3f1c2e-7c1a-4c55-9d7e-1a2b3c4d5e6f";
    private static final String API_KEY = "fake-api-key-1";
    private static final ProviderCallContext CONTEXT = new ProviderCallContext(
            UUID.randomUUID(), UUID.randomUUID(), ITEM_ID, ProviderCallReason.MANUAL_SYNC);

    @RegisterExtension
    static WireMockExtension pluggy = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    /** The production default: a busy build machine must not turn a slow local answer into a failure. */
    private static final Duration PATIENT = Duration.ofSeconds(10);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-18T12:00:00Z"));
    private final List<ProviderCall> recorded = new CopyOnWriteArrayList<>();
    private final ProviderCallRecorder recorder = new ProviderCallRecorder(null) {
        @Override
        public void record(ProviderCall call) {
            recorded.add(call);
        }
    };
    private PluggyFinancialDataProvider provider;

    @BeforeEach
    void setUp() {
        provider = providerAt(pluggy.baseUrl(), PATIENT);
        pluggy.stubFor(post("/auth").willReturn(okJson("{\"apiKey\":\"" + API_KEY + "\"}")));
    }

    @Test
    void recordsEachCallWithTheCallersContext() {
        stubItem();

        CONTEXT.call(() -> provider.findItem(ITEM_ID));

        assertThat(recorded).extracting(ProviderCall::operation).containsExactly("AUTH", "FIND_ITEM");
        assertThat(recorded).allSatisfy(call -> {
            assertThat(call.id()).isNotNull();
            assertThat(call.provider()).isEqualTo("PLUGGY");
            assertThat(call.userId()).isEqualTo(CONTEXT.userId());
            assertThat(call.connectionId()).isEqualTo(CONTEXT.connectionId());
            assertThat(call.providerItemId()).isEqualTo(ITEM_ID);
            assertThat(call.reason()).isEqualTo(ProviderCallReason.MANUAL_SYNC);
            assertThat(call.calledAt()).isEqualTo(clock.instant());
            assertThat(call.outcome()).isEqualTo(ProviderCallOutcome.SUCCESS);
            assertThat(call.httpStatus()).isEqualTo(200);
            assertThat(call.errorCode()).isNull();
            assertThat(call.durationMs()).isNotNegative();
        });
        assertThat(recorded.toString()).doesNotContain(API_KEY, "client-secret");
    }

    @Test
    void callsOutsideAContextAreRecordedWithoutUserConnectionOrReason() {
        stubItem();

        provider.findItem(ITEM_ID);

        assertThat(recorded).extracting(ProviderCall::operation, ProviderCall::userId, ProviderCall::connectionId,
                        ProviderCall::providerItemId, ProviderCall::reason)
                .containsExactly(tuple("AUTH", null, null, null, null), tuple("FIND_ITEM", null, null, null, null));
    }

    @Test
    void namesEachEndpointAndRecordsEveryPage() {
        pluggy.stubFor(get(urlPathEqualTo("/accounts")).willReturn(okJson(fixture("accounts.json"))));
        pluggy.stubFor(get(urlPathEqualTo("/v2/transactions")).willReturn(okJson("{\"results\":[],\"next\":null}")));
        pluggy.stubFor(get(urlPathEqualTo("/bills")).willReturn(okJson(fixture("bills.json"))));
        pluggy.stubFor(get(urlPathEqualTo("/investments")).withQueryParam("page", equalTo("1"))
                .willReturn(okJson(fixture("investments-page1.json"))));
        pluggy.stubFor(get(urlPathEqualTo("/investments")).withQueryParam("page", equalTo("2"))
                .willReturn(okJson(fixture("investments-page2.json"))));
        pluggy.stubFor(delete(urlEqualTo("/items/" + ITEM_ID)).willReturn(aResponse().withStatus(200)));

        CONTEXT.run(() -> {
            provider.listAccounts(ITEM_ID);
            provider.listTransactions("acc-checking", AccountKind.CHECKING, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-18"));
            provider.listBills("acc-card");
            provider.listInvestments(ITEM_ID);
            provider.deleteItem(ITEM_ID);
        });

        assertThat(recorded).extracting(ProviderCall::operation).containsExactly("AUTH", "LIST_ACCOUNTS",
                "LIST_TRANSACTIONS", "LIST_BILLS", "LIST_INVESTMENTS", "LIST_INVESTMENTS", "DELETE_ITEM");
    }

    @ParameterizedTest
    @CsvSource({
            "403, provider.auth_failed",
            "404, provider.not_found",
            "429, provider.rate_limited",
            "500, provider.unavailable",
            "503, provider.unavailable"})
    void anErrorStatusIsRecordedWithItsCode(int status, String errorCode) {
        pluggy.stubFor(get(urlEqualTo("/items/" + ITEM_ID)).willReturn(aResponse().withStatus(status)));

        assertThatThrownBy(() -> CONTEXT.call(() -> provider.findItem(ITEM_ID))).isInstanceOf(DomainException.class);

        assertThat(recorded.getLast()).satisfies(call -> {
            assertThat(call.operation()).isEqualTo("FIND_ITEM");
            assertThat(call.outcome()).isEqualTo(ProviderCallOutcome.FAILURE);
            assertThat(call.httpStatus()).isEqualTo(status);
            assertThat(call.errorCode()).isEqualTo(errorCode);
            assertThat(call.reason()).isEqualTo(ProviderCallReason.MANUAL_SYNC);
        });
    }

    @Test
    void aRevokedApiKeyIsRecordedWithTheRenewalAndTheRetry() {
        pluggy.stubFor(get(urlEqualTo("/items/" + ITEM_ID)).inScenario("revoked key")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(unauthorized())
                .willSetStateTo("key renewed"));
        pluggy.stubFor(get(urlEqualTo("/items/" + ITEM_ID)).inScenario("revoked key")
                .whenScenarioStateIs("key renewed")
                .willReturn(okJson(fixture("item.json"))));

        CONTEXT.call(() -> provider.findItem(ITEM_ID));

        assertThat(recorded).extracting(ProviderCall::operation, ProviderCall::httpStatus, ProviderCall::errorCode,
                        ProviderCall::reason)
                .containsExactly(
                        tuple("AUTH", 200, null, ProviderCallReason.MANUAL_SYNC),
                        tuple("FIND_ITEM", 401, ProviderErrors.AUTH_FAILED, ProviderCallReason.MANUAL_SYNC),
                        tuple("AUTH", 200, null, ProviderCallReason.MANUAL_SYNC),
                        tuple("FIND_ITEM", 200, null, ProviderCallReason.MANUAL_SYNC));
    }

    @Test
    void rejectedCredentialsAreRecordedOnTheAuthCall() {
        pluggy.stubFor(post("/auth").willReturn(unauthorized()));

        assertThatThrownBy(() -> provider.findItem(ITEM_ID)).isInstanceOf(DomainException.class);

        assertThat(recorded).extracting(ProviderCall::operation, ProviderCall::httpStatus, ProviderCall::errorCode)
                .containsExactly(tuple("AUTH", 401, ProviderErrors.AUTH_FAILED));
    }

    @Test
    void aDroppedConnectionIsRecordedWithoutAStatus() {
        pluggy.stubFor(get(urlEqualTo("/items/" + ITEM_ID)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> provider.findItem(ITEM_ID)).isInstanceOf(DomainException.class);

        assertThat(recorded.getLast()).extracting(ProviderCall::operation, ProviderCall::outcome, ProviderCall::httpStatus,
                        ProviderCall::errorCode)
                .containsExactly("FIND_ITEM", ProviderCallOutcome.FAILURE, null, PluggyAuditInterceptor.CONNECTION_FAILED);
    }

    @Test
    void aSlowAnswerIsRecordedAsATimeoutWithItsDuration() {
        // A second of patience, not less: on a busy machine the auth alone can take a few hundred ms.
        PluggyFinancialDataProvider impatient = providerAt(pluggy.baseUrl(), Duration.ofSeconds(1));
        pluggy.stubFor(get(urlEqualTo("/items/" + ITEM_ID)).willReturn(okJson(fixture("item.json")).withFixedDelay(5000)));

        assertThatThrownBy(() -> impatient.findItem(ITEM_ID)).isInstanceOf(DomainException.class);

        assertThat(recorded.getLast()).satisfies(call -> {
            assertThat(call.operation()).isEqualTo("FIND_ITEM");
            assertThat(call.httpStatus()).isNull();
            assertThat(call.errorCode()).isEqualTo(PluggyAuditInterceptor.TIMEOUT);
            assertThat(call.durationMs()).isGreaterThanOrEqualTo(1000);
        });
    }

    /** What a blocked egress looks like: nothing is listening, not even for the auth. */
    @Test
    void aProviderThatCannotBeReachedIsRecordedAsUnreachable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        PluggyFinancialDataProvider unreachable = providerAt("http://localhost:" + closedPort, PATIENT);

        assertThatThrownBy(() -> unreachable.findItem(ITEM_ID)).isInstanceOf(DomainException.class);

        assertThat(recorded).extracting(ProviderCall::operation, ProviderCall::httpStatus, ProviderCall::errorCode)
                .containsExactly(tuple("AUTH", null, PluggyAuditInterceptor.UNREACHABLE));
    }

    /** Not only I/O: whatever else the HTTP client throws leaves its record too. */
    @Test
    void aCallThatFailsWithAnUncheckedExceptionIsRecordedToo() {
        RestClient client = RestClient.builder()
                .baseUrl(pluggy.baseUrl())
                .requestInterceptor(new PluggyAuditInterceptor(recorder, clock))
                .requestInterceptor((request, body, execution) -> {
                    throw new IllegalStateException("HTTP client closed");
                })
                .build();

        assertThatThrownBy(() -> client.get().uri("/items/{id}", ITEM_ID).retrieve().toBodilessEntity())
                .hasMessageContaining("HTTP client closed");

        assertThat(recorded).extracting(ProviderCall::operation, ProviderCall::outcome, ProviderCall::httpStatus,
                        ProviderCall::errorCode)
                .containsExactly(tuple("FIND_ITEM", ProviderCallOutcome.FAILURE, null, PluggyAuditInterceptor.CONNECTION_FAILED));
    }

    @Test
    void endpointsWithoutANameAreNamedAfterTheirPathWithoutIds() {
        assertThat(PluggyOperations.of(HttpMethod.POST, URI.create("https://api.pluggy.ai/connect_token")))
                .isEqualTo("CREATE_CONNECT_TOKEN");
        assertThat(PluggyOperations.of(HttpMethod.GET, URI.create("https://api.pluggy.ai/items/demo-nubank")))
                .isEqualTo("FIND_ITEM");
        assertThat(PluggyOperations.of(HttpMethod.GET, URI.create("https://api.pluggy.ai/v2/transactions?after=a%2Bb")))
                .isEqualTo("LIST_TRANSACTIONS");
        assertThat(PluggyOperations.of(HttpMethod.PATCH, URI.create("https://api.pluggy.ai/items/" + ITEM_ID)))
                .isEqualTo("PATCH_ITEMS");
        assertThat(PluggyOperations.of(HttpMethod.GET, URI.create("https://api.pluggy.ai/connectors/201")))
                .isEqualTo("GET_CONNECTORS");
        assertThat(PluggyOperations.of(HttpMethod.GET, URI.create("https://api.pluggy.ai/investments/" + ITEM_ID + "/transactions")))
                .isEqualTo("GET_INVESTMENTS_TRANSACTIONS");
    }

    private PluggyFinancialDataProvider providerAt(String baseUrl, Duration timeout) {
        PluggyProperties properties = new PluggyProperties(baseUrl, "client-id", "client-secret", timeout, Duration.ofHours(2));
        RestClient.Builder builder = RestClient.builder().requestInterceptor(new PluggyAuditInterceptor(recorder, clock));
        return new PluggyFinancialDataProvider(new PluggyClient(PluggyConfig.restClient(builder, properties), properties, clock));
    }

    private void stubItem() {
        pluggy.stubFor(get(urlEqualTo("/items/" + ITEM_ID)).willReturn(okJson(fixture("item.json"))));
    }

    private static String fixture(String name) {
        try (InputStream stream = PluggyAuditInterceptorTest.class.getResourceAsStream("/pluggy/" + name)) {
            if (stream == null) {
                throw new IllegalStateException("Missing fixture " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
