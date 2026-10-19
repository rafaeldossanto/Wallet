package com.wallet.bff;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.wallet.bff.cache.ScreenCache;
import com.wallet.bff.config.GatewayKey;
import com.wallet.bff.service.SessionDelivery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DELETE /api/me: the core deletes the account, the BFF ends the session and forgets the user's screens. */
@SpringBootTest(properties = "wallet.rate-limit.auth-limit=1000")
class AccountDeletionTest extends BffTestSupport {

    @Autowired
    private ScreenCache cache;

    @Test
    void deletesInTheCoreEndsTheSessionAndForgetsTheScreens() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = token(userId);
        CORE.stubFor(WireMock.delete(urlEqualTo("/internal/me")).willReturn(aResponse().withStatus(204)));
        cache.get(userId, "home", "", () -> "before");

        mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(SessionDelivery.REFRESH_COOKIE + "="),
                        containsString("Max-Age=0"),
                        containsString("Path=/api/auth"))));

        CORE.verify(deleteRequestedFor(urlEqualTo("/internal/me"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + token))
                .withHeader(GatewayKey.HEADER, equalTo(GATEWAY_KEY)));
        assertThat(cache.get(userId, "home", "", () -> "after")).isEqualTo("after");
    }

    @Test
    void whenTheCoreRefusesNothingChangesHere() throws Exception {
        UUID userId = UUID.randomUUID();
        CORE.stubFor(WireMock.delete(urlEqualTo("/internal/me")).willReturn(aResponse().withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"internal.error\",\"message\":\"Unexpected error\"}")));
        cache.get(userId, "home", "", () -> "before");

        mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("internal.error"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(cache.get(userId, "home", "", () -> "after")).isEqualTo("before");
    }

    @Test
    void withoutATokenNothingReachesTheCore() throws Exception {
        mockMvc.perform(delete("/api/me"))
                .andExpect(status().isUnauthorized());

        CORE.verify(0, deleteRequestedFor(urlEqualTo("/internal/me")));
    }
}
