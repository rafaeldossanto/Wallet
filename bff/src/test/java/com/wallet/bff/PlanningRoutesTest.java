package com.wallet.bff;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The planning routes against a fake core: the month's plan with the banks' names, and its cache. */
@SpringBootTest(properties = "wallet.rate-limit.auth-limit=1000")
class PlanningRoutesTest extends BffTestSupport {

    private static final String CONNECTION_ID = "6f1c2a9e-0000-4000-8000-000000000001";
    private static final String CARD_ID = "6f1c2a9e-0000-4000-8000-000000000003";
    private static final String EXPENSE_ID = "6f1c2a9e-0000-4000-8000-0000000000e1";
    private static final String EXPENSE_BODY = "{\"name\":\"Faculdade\",\"amount\":\"450.00\",\"dayOfMonth\":10,\"category\":\"Education\"}";
    private static final String SUGGESTION_ID = "9f86d081884c7d659a2feaa0c55ad015-9";
    private static final String SUGGESTIONS_PATH = "/internal/planning/fixed-expense-suggestions";
    private static final String EXPENSE_JSON =
            "{\"id\":\"" + EXPENSE_ID + "\",\"name\":\"Faculdade\",\"amount\":\"450.00\",\"dayOfMonth\":10,\"category\":\"Education\"}";

    @Test
    void theMonthsPlanComesWithEachCardsBank() throws Exception {
        stubPlan("2026-10");
        stubConnections("ACTIVE", "2026-10-01T09:00:00Z");

        mockMvc.perform(get("/api/planning").queryParam("month", "2026-10").header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.salary.amount").value("3100.00"))
                .andExpect(jsonPath("$.salary.source").value("RECEIVED"))
                .andExpect(jsonPath("$.salary.predicted").value(false))
                .andExpect(jsonPath("$.bills", hasSize(1)))
                .andExpect(jsonPath("$.bills[0].institutionName").value("Banco Teste"))
                .andExpect(jsonPath("$.bills[0].institutionImageUrl").value("https://example.com/logo.png"))
                .andExpect(jsonPath("$.bills[0].cardName").value("Cartão"))
                .andExpect(jsonPath("$.bills[0].total").value("512.40"))
                .andExpect(jsonPath("$.bills[0].otherCharges").value("12.40"))
                .andExpect(jsonPath("$.bills[0].items[0].description").value("Fone de ouvido"))
                .andExpect(jsonPath("$.bills[0].items[1].installmentNumber").value(2))
                .andExpect(jsonPath("$.fixedExpenses[0].date").value("2026-10-10"))
                .andExpect(jsonPath("$.totals.leftover").value("2137.60"));

        CORE.verify(getRequestedFor(urlPathEqualTo("/internal/planning/month")).withQueryParam("month", equalTo("2026-10")));
    }

    @Test
    void thePlanIsCachedPerUserAndMonth() throws Exception {
        stubPlan("2026-10");
        stubPlan("2026-11");
        stubConnections("ACTIVE", "2026-10-01T09:00:00Z");
        String rafael = bearer(UUID.randomUUID());

        plan(rafael, "2026-10");
        plan(rafael, "2026-10");
        CORE.verify(1, monthRequested("2026-10"));

        plan(rafael, "2026-11");
        plan(bearer(UUID.randomUUID()), "2026-10");
        CORE.verify(2, monthRequested("2026-10"));
        CORE.verify(1, monthRequested("2026-11"));
    }

    @Test
    void changingAFixedExpenseDropsEveryCachedMonth() throws Exception {
        stubPlan("2026-10");
        stubPlan("2026-11");
        stubConnections("ACTIVE", "2026-10-01T09:00:00Z");
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/planning/fixed-expenses")).willReturn(aResponse()
                .withStatus(201).withHeader("Content-Type", "application/json").withBody(EXPENSE_JSON)));
        CORE.stubFor(WireMock.put(urlEqualTo("/internal/planning/fixed-expenses/" + EXPENSE_ID)).willReturn(okJson(EXPENSE_JSON)));
        CORE.stubFor(WireMock.delete(urlEqualTo("/internal/planning/fixed-expenses/" + EXPENSE_ID))
                .willReturn(aResponse().withStatus(204)));
        String rafael = bearer(UUID.randomUUID());

        plan(rafael, "2026-10");
        plan(rafael, "2026-11");
        mockMvc.perform(post("/api/planning/fixed-expenses").header(HttpHeaders.AUTHORIZATION, rafael)
                        .contentType(MediaType.APPLICATION_JSON).content(EXPENSE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(EXPENSE_ID))
                .andExpect(jsonPath("$.amount").value("450.00"));
        plan(rafael, "2026-10");
        plan(rafael, "2026-11");
        CORE.verify(2, monthRequested("2026-10"));
        CORE.verify(2, monthRequested("2026-11"));

        mockMvc.perform(put("/api/planning/fixed-expenses/" + EXPENSE_ID).header(HttpHeaders.AUTHORIZATION, rafael)
                        .contentType(MediaType.APPLICATION_JSON).content(EXPENSE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayOfMonth").value(10));
        plan(rafael, "2026-10");
        CORE.verify(3, monthRequested("2026-10"));

        mockMvc.perform(delete("/api/planning/fixed-expenses/" + EXPENSE_ID).header(HttpHeaders.AUTHORIZATION, rafael))
                .andExpect(status().isNoContent());
        plan(rafael, "2026-10");
        CORE.verify(4, monthRequested("2026-10"));

        CORE.verify(postRequestedFor(urlEqualTo("/internal/planning/fixed-expenses"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo(rafael))
                .withRequestBody(equalToJson(EXPENSE_BODY)));
        CORE.verify(putRequestedFor(urlEqualTo("/internal/planning/fixed-expenses/" + EXPENSE_ID))
                .withRequestBody(equalToJson(EXPENSE_BODY)));
        CORE.verify(deleteRequestedFor(urlEqualTo("/internal/planning/fixed-expenses/" + EXPENSE_ID)));
    }

    @Test
    void settingOrClearingTheExpectedSalaryDropsTheCachedPlan() throws Exception {
        stubPlan("2026-11");
        stubConnections("ACTIVE", "2026-10-01T09:00:00Z");
        CORE.stubFor(WireMock.put(urlEqualTo("/internal/planning/expected-salary")).willReturn(okJson("{\"amount\":\"3400.00\"}")));
        CORE.stubFor(WireMock.delete(urlEqualTo("/internal/planning/expected-salary")).willReturn(aResponse().withStatus(204)));
        String rafael = bearer(UUID.randomUUID());

        plan(rafael, "2026-11");
        mockMvc.perform(put("/api/planning/expected-salary").header(HttpHeaders.AUTHORIZATION, rafael)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"3400.00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("3400.00"));
        plan(rafael, "2026-11");
        mockMvc.perform(delete("/api/planning/expected-salary").header(HttpHeaders.AUTHORIZATION, rafael))
                .andExpect(status().isNoContent());
        plan(rafael, "2026-11");

        CORE.verify(3, monthRequested("2026-11"));
        CORE.verify(putRequestedFor(urlEqualTo("/internal/planning/expected-salary"))
                .withRequestBody(equalToJson("{\"amount\":\"3400.00\"}")));
    }

    /** The app lists the connections while a sync runs: a new last sync there drops the cached plan. */
    @Test
    void aSyncThatLandsDropsTheCachedPlan() throws Exception {
        stubPlan("2026-10");
        stubConnections("ACTIVE", "2026-10-01T09:00:00Z");
        String rafael = bearer(UUID.randomUUID());
        plan(rafael, "2026-10");
        plan(rafael, "2026-10");
        CORE.verify(1, monthRequested("2026-10"));

        stubConnections("ACTIVE", "2026-10-01T15:00:00Z");
        mockMvc.perform(get("/api/connections").header(HttpHeaders.AUTHORIZATION, rafael)).andExpect(status().isOk());
        plan(rafael, "2026-10");

        CORE.verify(2, monthRequested("2026-10"));
    }

    @Test
    void aPlanComposedDuringASyncIsNotKept() throws Exception {
        stubPlan("2026-10");
        stubConnections("SYNCING", "2026-10-01T09:00:00Z");
        String rafael = bearer(UUID.randomUUID());

        plan(rafael, "2026-10");
        plan(rafael, "2026-10");

        CORE.verify(2, monthRequested("2026-10"));
    }

    @Test
    void withoutTheNamesThePlanStillComesBackButIsNotKept() throws Exception {
        stubPlan("2026-10");
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/connections")).willReturn(aResponse().withStatus(500)));
        String rafael = bearer(UUID.randomUUID());

        mockMvc.perform(get("/api/planning").queryParam("month", "2026-10").header(HttpHeaders.AUTHORIZATION, rafael))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bills[0].cardName").value("Cartão"))
                .andExpect(jsonPath("$.bills[0].institutionName").value(nullValue()));
        plan(rafael, "2026-10");

        CORE.verify(2, monthRequested("2026-10"));
    }

    @Test
    void theCoresCodesReachTheApp() throws Exception {
        CORE.stubFor(WireMock.get(urlPathEqualTo("/internal/planning/month")).willReturn(aResponse()
                .withStatus(400).withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"request.invalid_parameter\",\"message\":\"A request parameter is missing or invalid\"}")));
        stubConnections("ACTIVE", "2026-10-01T09:00:00Z");
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/planning/fixed-expenses")).willReturn(aResponse()
                .withStatus(400).withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"validation.failed\",\"message\":\"Request has invalid fields\"}")));
        CORE.stubFor(WireMock.delete(urlEqualTo("/internal/planning/fixed-expenses/" + EXPENSE_ID)).willReturn(aResponse()
                .withStatus(404).withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"planning.fixed_expense_not_found\",\"message\":\"No such fixed expense\"}")));
        String rafael = bearer(UUID.randomUUID());

        mockMvc.perform(get("/api/planning").queryParam("month", "outubro").header(HttpHeaders.AUTHORIZATION, rafael))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.invalid_parameter"));
        mockMvc.perform(post("/api/planning/fixed-expenses").header(HttpHeaders.AUTHORIZATION, rafael)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"amount\":\"-1\",\"dayOfMonth\":40}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
        mockMvc.perform(delete("/api/planning/fixed-expenses/" + EXPENSE_ID).header(HttpHeaders.AUTHORIZATION, rafael))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("planning.fixed_expense_not_found"));
    }

    @Test
    void theFixedExpensesAreListedAndEverythingNeedsASession() throws Exception {
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/planning/fixed-expenses")).willReturn(okJson("[" + EXPENSE_JSON + "]")));

        mockMvc.perform(get("/api/planning/fixed-expenses").header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Faculdade"));

        mockMvc.perform(get("/api/planning")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/planning/fixed-expenses").contentType(MediaType.APPLICATION_JSON).content(EXPENSE_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/planning/expected-salary").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"1.00\"}"))
                .andExpect(status().isUnauthorized());
        CORE.verify(0, getRequestedFor(urlPathEqualTo("/internal/planning/month")));
        CORE.verify(0, postRequestedFor(urlEqualTo("/internal/planning/fixed-expenses")));
    }

    @Test
    void theSuggestedFixedExpensesComeAsTheCoreComputesThemEveryTime() throws Exception {
        CORE.stubFor(WireMock.get(urlEqualTo(SUGGESTIONS_PATH)).willReturn(okJson("""
                [{"id":"%s","name":"Condominio Edificio Sol","amount":"690.00","dayOfMonth":9,"category":"Housing",
                  "months":4,"lastDate":"2026-10-09"}]
                """.formatted(SUGGESTION_ID))));
        String rafael = bearer(UUID.randomUUID());

        mockMvc.perform(get("/api/planning/fixed-expense-suggestions").header(HttpHeaders.AUTHORIZATION, rafael))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(SUGGESTION_ID))
                .andExpect(jsonPath("$[0].name").value("Condominio Edificio Sol"))
                .andExpect(jsonPath("$[0].amount").value("690.00"))
                .andExpect(jsonPath("$[0].dayOfMonth").value(9))
                .andExpect(jsonPath("$[0].category").value("Housing"))
                .andExpect(jsonPath("$[0].months").value(4))
                .andExpect(jsonPath("$[0].lastDate").value("2026-10-09"));
        mockMvc.perform(get("/api/planning/fixed-expense-suggestions").header(HttpHeaders.AUTHORIZATION, rafael))
                .andExpect(status().isOk());

        CORE.verify(2, getRequestedFor(urlEqualTo(SUGGESTIONS_PATH)).withHeader(HttpHeaders.AUTHORIZATION, equalTo(rafael)));
        mockMvc.perform(get("/api/planning/fixed-expense-suggestions")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/planning/fixed-expense-suggestions/" + SUGGESTION_ID)).andExpect(status().isUnauthorized());
        CORE.verify(2, getRequestedFor(urlEqualTo(SUGGESTIONS_PATH)));
        CORE.verify(0, deleteRequestedFor(urlPathMatching(SUGGESTIONS_PATH + "/.*")));
    }

    /** A dismissed suggestion changes nothing in the month's plan, so the cached plan stays. */
    @Test
    void dismissingASuggestionGoesToTheCoreAndKeepsTheCachedPlan() throws Exception {
        stubPlan("2026-10");
        stubConnections("ACTIVE", "2026-10-01T09:00:00Z");
        CORE.stubFor(WireMock.delete(urlEqualTo(SUGGESTIONS_PATH + "/" + SUGGESTION_ID)).willReturn(aResponse().withStatus(204)));
        String rafael = bearer(UUID.randomUUID());

        plan(rafael, "2026-10");
        mockMvc.perform(delete("/api/planning/fixed-expense-suggestions/" + SUGGESTION_ID).header(HttpHeaders.AUTHORIZATION, rafael))
                .andExpect(status().isNoContent());
        plan(rafael, "2026-10");

        CORE.verify(1, monthRequested("2026-10"));
        CORE.verify(deleteRequestedFor(urlEqualTo(SUGGESTIONS_PATH + "/" + SUGGESTION_ID))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo(rafael)));
    }

    /** The BFF does not judge the id: the core does, and an id with URL characters travels encoded. */
    @Test
    void anUnknownSuggestionComesBackWithTheCoresCode() throws Exception {
        CORE.stubFor(WireMock.delete(urlPathMatching(SUGGESTIONS_PATH + "/.*")).willReturn(aResponse()
                .withStatus(404).withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"planning.fixed_expense_suggestion_not_found\",\"message\":\"No such fixed expense suggestion\"}")));
        String rafael = bearer(UUID.randomUUID());

        mockMvc.perform(delete("/api/planning/fixed-expense-suggestions/{id}", "a b?c").header(HttpHeaders.AUTHORIZATION, rafael))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("planning.fixed_expense_suggestion_not_found"));

        CORE.verify(deleteRequestedFor(urlEqualTo(SUGGESTIONS_PATH + "/a%20b%3Fc")));
    }

    // ---- fixtures ----------------------------------------------------------------------------

    private void plan(String bearer, String month) throws Exception {
        mockMvc.perform(get("/api/planning").queryParam("month", month).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    private static RequestPatternBuilder monthRequested(String month) {
        return getRequestedFor(urlPathEqualTo("/internal/planning/month")).withQueryParam("month", equalTo(month));
    }

    /** Made-up numbers: salary 3100.00, one bill of 512.40 and one fixed expense of 450.00. */
    private static void stubPlan(String month) {
        CORE.stubFor(WireMock.get(urlPathEqualTo("/internal/planning/month")).withQueryParam("month", equalTo(month))
                .willReturn(okJson("""
                        {"month":"%s",
                         "salary":{"amount":"3100.00","date":"2026-10-05","predicted":false,"source":"RECEIVED","expectedAmount":null},
                         "bills":[{"accountId":"%s","connectionId":"%s","cardName":"Cartão","numberLastDigits":"1111",
                                   "dueDate":"2026-10-10","closingDate":"2026-10-03","total":"512.40","open":false,
                                   "items":[{"transactionId":"6f1c2a9e-0000-4000-8000-0000000000c1","date":"2026-09-20",
                                             "description":"Fone de ouvido","amount":"200.00","direction":"OUTFLOW",
                                             "category":"Electronics","installmentNumber":null,"installmentTotal":null},
                                            {"transactionId":"6f1c2a9e-0000-4000-8000-0000000000c2","date":"2026-08-15",
                                             "description":"Bicicleta","amount":"150.00","direction":"OUTFLOW",
                                             "category":"Sports practice","installmentNumber":2,"installmentTotal":3}],
                                   "otherCharges":"12.40"}],
                         "fixedExpenses":[{"id":"%s","name":"Faculdade","amount":"450.00","dayOfMonth":10,
                                           "date":"2026-10-10","category":"Education"}],
                         "totals":{"salary":"3100.00","bills":"512.40","fixedExpenses":"450.00","leftover":"2137.60"}}
                        """.formatted(month, CARD_ID, CONNECTION_ID, EXPENSE_ID))));
    }

    private static void stubConnections(String status, String lastSyncedAt) {
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/connections")).willReturn(okJson("""
                [{"id":"%s","institutionName":"Banco Teste","institutionImageUrl":"https://example.com/logo.png",
                  "status":"%s","lastSyncedAt":"%s","createdAt":"2026-09-01T12:00:00Z"}]
                """.formatted(CONNECTION_ID, status, lastSyncedAt))));
    }
}
