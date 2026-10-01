package com.wallet.bff;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.wallet.bff.config.ClockConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every /api route against a fake core: what reaches the core and what the app gets back. */
@SpringBootTest(properties = "wallet.rate-limit.auth-limit=1000")
class BffRoutesTest extends BffTestSupport {

    private static final String CLIENT = "X-Wallet-Client";
    private static final String CONNECTION_ID = "6f1c2a9e-0000-4000-8000-000000000001";
    private static final String CHECKING_ID = "6f1c2a9e-0000-4000-8000-000000000002";
    private static final String CARD_ID = "6f1c2a9e-0000-4000-8000-000000000003";

    private static final String USER_JSON =
            "{\"id\":\"%s\",\"email\":\"rafael@example.com\",\"displayName\":\"Rafael\",\"createdAt\":\"2026-10-01T12:00:00Z\"}";

    // ---- auth --------------------------------------------------------------------------------

    @Test
    void mobileLoginGetsBothTokensInTheBody() throws Exception {
        stubTokens("/internal/auth/login", "access-1", "refresh-1");

        mockMvc.perform(post("/api/auth/login").header(CLIENT, "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rafael@example.com\",\"password\":\"secret-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-1"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").value("refresh-1"))
                .andExpect(jsonPath("$.refreshTokenExpiresAt").exists())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        CORE.verify(postRequestedFor(urlEqualTo("/internal/auth/login"))
                .withRequestBody(equalToJson("{\"email\":\"rafael@example.com\",\"password\":\"secret-password\"}")));
    }

    @Test
    void webLoginKeepsTheRefreshTokenOutOfReachOfJavaScript() throws Exception {
        stubTokens("/internal/auth/login", "access-1", "refresh-1");

        mockMvc.perform(post("/api/auth/login").header(CLIENT, "web")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rafael@example.com\",\"password\":\"secret-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-1"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.refreshTokenExpiresAt").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("wallet_refresh=refresh-1"),
                        containsString("HttpOnly"),
                        containsString("Secure"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/api/auth"),
                        containsString("Max-Age=259"))));
    }

    @Test
    void authRoutesNeedToKnowWhichAppIsCalling() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rafael@example.com\",\"password\":\"secret-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.client_required"));
        mockMvc.perform(post("/api/auth/refresh").header(CLIENT, "desktop")
                        .cookie(new Cookie("wallet_refresh", "refresh-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.client_required"));

        CORE.verify(0, postRequestedFor(urlEqualTo("/internal/auth/login")));
        CORE.verify(0, postRequestedFor(urlEqualTo("/internal/auth/refresh")));
    }

    @Test
    void webRefreshReadsTheCookieAndRotatesIt() throws Exception {
        stubTokens("/internal/auth/refresh", "access-2", "refresh-2");

        mockMvc.perform(post("/api/auth/refresh").header(CLIENT, "web")
                        .cookie(new Cookie("wallet_refresh", "refresh-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-2"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("wallet_refresh=refresh-2")));

        CORE.verify(postRequestedFor(urlEqualTo("/internal/auth/refresh"))
                .withRequestBody(equalToJson("{\"refreshToken\":\"refresh-1\"}")));
    }

    @Test
    void webRefreshWithoutTheCookieStopsAtTheBff() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").header(CLIENT, "web"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_refresh_token"));

        CORE.verify(0, postRequestedFor(urlEqualTo("/internal/auth/refresh")));
    }

    @Test
    void refusedWebRefreshDropsTheCookie() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/refresh")).willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"auth.refresh_token_reused\",\"message\":\"Session revoked\"}")));

        mockMvc.perform(post("/api/auth/refresh").header(CLIENT, "web")
                        .cookie(new Cookie("wallet_refresh", "stolen")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.refresh_token_reused"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("wallet_refresh="),
                        containsString("Max-Age=0"))));
    }

    @Test
    void mobileRefreshUsesTheBodyAndIgnoresAStaleBearer() throws Exception {
        stubTokens("/internal/auth/refresh", "access-2", "refresh-2");
        String expired = token(UUID.randomUUID(), Instant.now().minus(Duration.ofMinutes(1)), "wallet-core", SIGNING_KEY);

        mockMvc.perform(post("/api/auth/refresh").header(CLIENT, "mobile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-2"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-2"));

        CORE.verify(postRequestedFor(urlEqualTo("/internal/auth/refresh"))
                .withHeader(HttpHeaders.AUTHORIZATION, absent())
                .withRequestBody(equalToJson("{\"refreshToken\":\"refresh-1\"}")));
    }

    @Test
    void webLogoutRevokesTheSessionAndDropsTheCookie() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/logout")).willReturn(aResponse().withStatus(204)));

        mockMvc.perform(post("/api/auth/logout").header(CLIENT, "web")
                        .cookie(new Cookie("wallet_refresh", "refresh-1")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        CORE.verify(postRequestedFor(urlEqualTo("/internal/auth/logout"))
                .withRequestBody(equalToJson("{\"refreshToken\":\"refresh-1\"}")));
    }

    @Test
    void registerIsRelayedWithTheCoresAnswer() throws Exception {
        UUID userId = UUID.randomUUID();
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/register"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody(USER_JSON.formatted(userId))));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rafael@example.com\",\"password\":\"secret-password\",\"displayName\":\"Rafael\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()));

        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/register")).willReturn(aResponse()
                .withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"identity.email_taken\",\"message\":\"Email already registered\"}")));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rafael@example.com\",\"password\":\"secret-password\",\"displayName\":\"Rafael\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("identity.email_taken"));
    }

    // ---- home --------------------------------------------------------------------------------

    @Test
    void homeComposesTheCoresRoutesIntoOneScreen() throws Exception {
        stubHome();

        mockMvc.perform(get("/api/home").header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.netWorth").value("1234.50"))
                .andExpect(jsonPath("$.institutions", hasSize(1)))
                .andExpect(jsonPath("$.institutions[0].connectionId").value(CONNECTION_ID))
                .andExpect(jsonPath("$.institutions[0].institutionName").value("Banco Teste"))
                .andExpect(jsonPath("$.institutions[0].accounts", hasSize(1)))
                .andExpect(jsonPath("$.institutions[0].accounts[0].id").value(CHECKING_ID))
                .andExpect(jsonPath("$.institutions[0].accounts[0].balance").value("2000.00"))
                .andExpect(jsonPath("$.institutions[0].accounts[0].creditCard").doesNotExist())
                .andExpect(jsonPath("$.creditCards", hasSize(1)))
                .andExpect(jsonPath("$.creditCards[0].accountId").value(CARD_ID))
                .andExpect(jsonPath("$.recentTransactions", hasSize(1)))
                .andExpect(jsonPath("$.recentTransactions[0].amount").value("42.90"))
                .andExpect(jsonPath("$.unavailable", hasSize(0)))
                .andExpect(jsonPath("$.complete").doesNotExist());

        LocalDate today = ClockConfig.today(Clock.systemUTC());
        CORE.verify(getRequestedFor(urlPathEqualTo("/internal/transactions"))
                .withQueryParam("from", equalTo(today.minusDays(29).toString()))
                .withQueryParam("to", equalTo(today.toString()))
                .withQueryParam("pageSize", equalTo("5")));
    }

    @Test
    void homeShowsWhatLoadedWhenOnePartFails() throws Exception {
        stubHome();
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/credit-cards")).willReturn(aResponse().withStatus(500)));

        mockMvc.perform(get("/api/home").header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.netWorth").value("1234.50"))
                .andExpect(jsonPath("$.institutions", hasSize(1)))
                .andExpect(jsonPath("$.creditCards", hasSize(0)))
                .andExpect(jsonPath("$.unavailable[0]").value("creditCards"));
    }

    @Test
    void homeFailsWholeWhenTheCoreSaysTheSessionIsGone() throws Exception {
        stubHome();
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/overview")).willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"auth.unauthenticated\",\"message\":\"Session expired\"}")));

        mockMvc.perform(get("/api/home").header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthenticated"));
    }

    @Test
    void homeIsCachedPerUserUntilASyncStarts() throws Exception {
        stubHome();
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/connections/" + CONNECTION_ID + "/sync")).willReturn(aResponse()
                .withStatus(202)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"syncRunId\":\"6f1c2a9e-0000-4000-8000-0000000000aa\",\"status\":\"RUNNING\"}")));
        String rafael = bearer(UUID.randomUUID());
        String someoneElse = bearer(UUID.randomUUID());

        home(rafael);
        home(rafael);
        CORE.verify(1, getRequestedFor(urlEqualTo("/internal/overview")));

        home(someoneElse);
        CORE.verify(2, getRequestedFor(urlEqualTo("/internal/overview")));

        mockMvc.perform(post("/api/connections/" + CONNECTION_ID + "/sync").header(HttpHeaders.AUTHORIZATION, rafael))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"));
        home(rafael);
        CORE.verify(3, getRequestedFor(urlEqualTo("/internal/overview")));
    }

    @Test
    void partialHomeIsNeverCached() throws Exception {
        stubHome();
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/credit-cards")).willReturn(aResponse().withStatus(500)));
        String rafael = bearer(UUID.randomUUID());

        home(rafael);
        home(rafael);

        CORE.verify(2, getRequestedFor(urlEqualTo("/internal/overview")));
    }

    // ---- other screens -----------------------------------------------------------------------

    @Test
    void statementFiltersReachTheCoreIntact() throws Exception {
        CORE.stubFor(WireMock.get(urlPathEqualTo("/internal/transactions")).willReturn(okJson(
                "{\"items\":[],\"page\":0,\"pageSize\":50,\"total\":0,\"totalPages\":0}")));

        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID()))
                        .queryParam("from", "2026-09-01")
                        .queryParam("to", "2026-09-30")
                        .queryParam("direction", "OUTFLOW")
                        .queryParam("q", "café & pão+1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        CORE.verify(getRequestedFor(urlPathEqualTo("/internal/transactions"))
                .withQueryParam("from", equalTo("2026-09-01"))
                .withQueryParam("to", equalTo("2026-09-30"))
                .withQueryParam("direction", equalTo("OUTFLOW"))
                .withQueryParam("q", equalTo("café & pão+1"))
                .withQueryParam("accountId", absent())
                .withQueryParam("page", absent()));
    }

    @Test
    void insightsBringTheMonthAndSixMonthsOfNetWorth() throws Exception {
        CORE.stubFor(WireMock.get(urlPathEqualTo("/internal/insights/spending-by-category")).willReturn(okJson(
                "{\"month\":\"2026-09\",\"total\":\"300.00\",\"categories\":[{\"category\":\"Food\",\"total\":\"300.00\"}]}")));
        CORE.stubFor(WireMock.get(urlPathEqualTo("/internal/insights/net-worth")).willReturn(okJson(
                "{\"points\":[{\"date\":\"2026-09-30\",\"netWorth\":\"1000.00\",\"cash\":\"1200.00\","
                        + "\"investments\":\"0.00\",\"creditCardDebt\":\"200.00\"}]}")));

        mockMvc.perform(get("/api/insights").queryParam("month", "2026-09")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spending.categories[0].total").value("300.00"))
                .andExpect(jsonPath("$.netWorth.points[0].netWorth").value("1000.00"));

        LocalDate today = ClockConfig.today(Clock.systemUTC());
        CORE.verify(getRequestedFor(urlPathEqualTo("/internal/insights/spending-by-category"))
                .withQueryParam("month", equalTo("2026-09")));
        CORE.verify(getRequestedFor(urlPathEqualTo("/internal/insights/net-worth"))
                .withQueryParam("from", equalTo(today.minusDays(182).toString()))
                .withQueryParam("to", equalTo(today.toString())));
    }

    @Test
    void cardsBillsAndInvestmentsAreRelayed() throws Exception {
        stubHome();
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/credit-cards/" + CARD_ID + "/bills")).willReturn(okJson(
                "[{\"id\":\"6f1c2a9e-0000-4000-8000-0000000000b1\",\"dueDate\":\"2026-10-10\",\"closingDate\":\"2026-10-03\","
                        + "\"totalAmount\":\"850.10\",\"minimumPayment\":\"85.01\",\"currencyCode\":\"BRL\"}]")));
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/investments")).willReturn(okJson(
                "{\"total\":\"5000.00\",\"byKind\":[{\"kind\":\"FIXED_INCOME\",\"total\":\"5000.00\"}],\"positions\":[]}")));
        String rafael = bearer(UUID.randomUUID());

        mockMvc.perform(get("/api/cards").header(HttpHeaders.AUTHORIZATION, rafael))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentBill.totalAmount").value("850.10"));
        mockMvc.perform(get("/api/cards/" + CARD_ID + "/bills").header(HttpHeaders.AUTHORIZATION, rafael))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].minimumPayment").value("85.01"));
        mockMvc.perform(get("/api/investments").header(HttpHeaders.AUTHORIZATION, rafael))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value("5000.00"));
    }

    @Test
    void linkingAndUnlinkingAreRelayed() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/connections")).willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(connectionJson())));
        CORE.stubFor(WireMock.delete(urlEqualTo("/internal/connections/" + CONNECTION_ID))
                .willReturn(aResponse().withStatus(204)));
        String rafael = bearer(UUID.randomUUID());

        mockMvc.perform(post("/api/connections").header(HttpHeaders.AUTHORIZATION, rafael)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerItemId\":\"item-123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CONNECTION_ID));
        mockMvc.perform(delete("/api/connections/" + CONNECTION_ID).header(HttpHeaders.AUTHORIZATION, rafael))
                .andExpect(status().isNoContent());

        CORE.verify(postRequestedFor(urlEqualTo("/internal/connections"))
                .withRequestBody(equalToJson("{\"providerItemId\":\"item-123\"}")));
        CORE.verify(deleteRequestedFor(urlEqualTo("/internal/connections/" + CONNECTION_ID)));
    }

    // ---- fixtures ----------------------------------------------------------------------------

    private void home(String bearer) throws Exception {
        mockMvc.perform(get("/api/home").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
    }

    private static void stubTokens(String path, String accessToken, String refreshToken) {
        CORE.stubFor(WireMock.post(urlEqualTo(path)).willReturn(okJson("""
                {"accessToken":"%s","tokenType":"Bearer","expiresIn":900,
                 "refreshToken":"%s","refreshTokenExpiresAt":"%s"}
                """.formatted(accessToken, refreshToken, Instant.now().plus(Duration.ofDays(30))))));
    }

    private static void stubHome() {
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/overview")).willReturn(okJson("""
                {"netWorth":"1234.50","cashBalance":"2000.00","creditCardDebt":"765.50","investmentsTotal":"0.00",
                 "month":"2026-10","monthIncome":"3000.00","monthExpenses":"1200.00","lastSyncedAt":"2026-10-01T09:00:00Z"}
                """)));
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/accounts")).willReturn(okJson("""
                [{"id":"%s","connectionId":"%s","kind":"CHECKING","name":"Conta Corrente","numberLastDigits":"1234",
                  "currencyCode":"BRL","balance":"2000.00","updatedAt":"2026-10-01T09:00:00Z"},
                 {"id":"%s","connectionId":"%s","kind":"CREDIT_CARD","name":"Cartao","numberLastDigits":"9876",
                  "currencyCode":"BRL","balance":"765.50","creditLimit":"5000.00","availableCredit":"4234.50",
                  "updatedAt":"2026-10-01T09:00:00Z"}]
                """.formatted(CHECKING_ID, CONNECTION_ID, CARD_ID, CONNECTION_ID))));
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/connections")).willReturn(okJson("[" + connectionJson() + "]")));
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/credit-cards")).willReturn(okJson("""
                [{"accountId":"%s","connectionId":"%s","name":"Cartao","numberLastDigits":"9876","currencyCode":"BRL",
                  "creditLimit":"5000.00","availableCredit":"4234.50","currentBalance":"765.50",
                  "currentBill":{"id":"6f1c2a9e-0000-4000-8000-0000000000b1","dueDate":"2026-10-10",
                                 "closingDate":"2026-10-03","totalAmount":"850.10","minimumPayment":"85.01",
                                 "currencyCode":"BRL"}}]
                """.formatted(CARD_ID, CONNECTION_ID))));
        CORE.stubFor(WireMock.get(urlPathEqualTo("/internal/transactions")).willReturn(okJson("""
                {"items":[{"id":"6f1c2a9e-0000-4000-8000-0000000000c1","accountId":"%s","bookedOn":"2026-09-30",
                           "description":"Padaria","amount":"42.90","direction":"OUTFLOW","status":"POSTED",
                           "category":"Food"}],
                 "page":0,"pageSize":5,"total":1,"totalPages":1}
                """.formatted(CHECKING_ID))));
    }

    private static String connectionJson() {
        return """
                {"id":"%s","institutionName":"Banco Teste","institutionImageUrl":"https://example.com/logo.png",
                 "status":"ACTIVE","lastSyncedAt":"2026-10-01T09:00:00Z","createdAt":"2026-09-01T12:00:00Z"}
                """.formatted(CONNECTION_ID);
    }
}
