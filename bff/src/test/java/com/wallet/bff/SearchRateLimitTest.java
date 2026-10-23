package com.wallet.bff;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Its own context: a search limit below the general one, which the other tests would trip. */
@SpringBootTest(properties = {"wallet.rate-limit.default-limit=10", "wallet.rate-limit.search-limit=2"})
class SearchRateLimitTest extends BffTestSupport {

    private static final String PAGE = "{\"items\":[],\"page\":1,\"pageSize\":50,\"total\":0,\"totalPages\":0}";

    /** Each search makes the core decrypt the month: a user gets fewer of them than plain requests (A10). */
    @Test
    void textSearchesHaveALowerLimitOfTheirOwn() throws Exception {
        CORE.stubFor(WireMock.get(urlPathEqualTo("/internal/transactions")).willReturn(okJson(PAGE)));
        String rafael = bearer(UUID.randomUUID());

        search(rafael, "padaria").andExpect(status().isOk());
        search(rafael, "mercado").andExpect(status().isOk());
        search(rafael, "farmacia")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("rate_limit.exceeded"));

        statement(rafael).andExpect(status().isOk());
        search(bearer(UUID.randomUUID()), "farmacia").andExpect(status().isOk());
        CORE.verify(4, getRequestedFor(urlPathEqualTo("/internal/transactions")));
    }

    /** An empty q is no search: the core pages it in the database. */
    @Test
    void aBlankQueryIsThePlainStatement() throws Exception {
        CORE.stubFor(WireMock.get(urlPathEqualTo("/internal/transactions")).willReturn(okJson(PAGE)));
        String rafael = bearer(UUID.randomUUID());

        for (int attempt = 0; attempt < 4; attempt++) {
            search(rafael, " ").andExpect(status().isOk());
        }
    }

    /** Answered from memory, outside the rate limit: a load balancer polling it never gets a 429. */
    @Test
    void theLivenessProbeIsNeverRateLimited() throws Exception {
        for (int attempt = 0; attempt < 15; attempt++) {
            mockMvc.perform(get("/livez")).andExpect(status().isOk());
        }
    }

    private ResultActions search(String bearer, String q) throws Exception {
        return mockMvc.perform(get("/api/transactions").queryParam("q", q).header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions statement(String bearer) throws Exception {
        return mockMvc.perform(get("/api/transactions").header(HttpHeaders.AUTHORIZATION, bearer));
    }
}
