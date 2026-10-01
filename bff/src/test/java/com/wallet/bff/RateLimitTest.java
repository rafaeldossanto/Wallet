package com.wallet.bff;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.springframework.test.web.servlet.ResultActions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Its own context: tiny limits that would trip every other test. */
@SpringBootTest(properties = {"wallet.rate-limit.default-limit=3", "wallet.rate-limit.auth-limit=2"})
class RateLimitTest extends BffTestSupport {

    @Test
    void eachUserHasItsOwnBudget() throws Exception {
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/me")).willReturn(okJson("{\"id\":\"%s\"}".formatted(UUID.randomUUID()))));
        String first = bearer(UUID.randomUUID());

        for (int attempt = 0; attempt < 3; attempt++) {
            me(first).andExpect(status().isOk());
        }
        me(first)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("rate_limit.exceeded"));

        me(bearer(UUID.randomUUID())).andExpect(status().isOk());
    }

    private ResultActions me(String bearer) throws Exception {
        return mockMvc.perform(get("/api/me")
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }
}
