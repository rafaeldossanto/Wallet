package com.wallet.bff;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The export of the user's data (LGPD, art. 18, II and V): the core's file, as a download, a few times a day. */
@SpringBootTest
class DataExportTest extends BffTestSupport {

    private static final String FILE = "{\"schemaVersion\":1,\"exportedAt\":\"2026-10-23T12:00:00Z\","
            + "\"banking\":{\"transactions\":[{\"description\":\"Supermercado Zé\",\"amount\":\"123.45\"}]}}";

    @Test
    void servesTheCoresFileUntouchedAsADownload() throws Exception {
        CORE.stubFor(WireMock.get(urlPathEqualTo("/internal/me/export")).willReturn(okJson(FILE)));

        export(bearer(UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.matchesPattern("attachment; filename=\"wallet-meus-dados-\\d{4}-\\d{2}-\\d{2}\\.json\"")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(FILE));

        CORE.verify(getRequestedFor(urlPathEqualTo("/internal/me/export"))
                .withHeader(HttpHeaders.AUTHORIZATION, WireMock.matching("Bearer .+"))
                .withHeader("X-Gateway-Key", equalTo(GATEWAY_KEY)));
    }

    /** Each export reads everything the user has: three a day, per user (rate_limit.exceeded). */
    @Test
    void aUserGetsThreeADay() throws Exception {
        CORE.stubFor(WireMock.get(urlPathEqualTo("/internal/me/export")).willReturn(okJson(FILE)));
        String rafael = bearer(UUID.randomUUID());

        export(rafael).andExpect(status().isOk());
        export(rafael).andExpect(status().isOk());
        export(rafael).andExpect(status().isOk());
        export(rafael)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "86400"))
                .andExpect(jsonPath("$.code").value("rate_limit.exceeded"));

        export(bearer(UUID.randomUUID())).andExpect(status().isOk());
        CORE.verify(4, getRequestedFor(urlPathEqualTo("/internal/me/export")));
    }

    @Test
    void needsASignedInUser() throws Exception {
        mockMvc.perform(get("/api/me/export")).andExpect(status().isUnauthorized());

        CORE.verify(0, getRequestedFor(urlPathEqualTo("/internal/me/export")));
    }

    @Test
    void relaysTheCoresRefusal() throws Exception {
        CORE.stubFor(WireMock.get(urlPathEqualTo("/internal/me/export")).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"auth.unauthenticated\",\"message\":\"User of this token no longer exists\"}")));

        export(bearer(UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthenticated"));
    }

    private ResultActions export(String bearer) throws Exception {
        return mockMvc.perform(get("/api/me/export").header(HttpHeaders.AUTHORIZATION, bearer));
    }
}
