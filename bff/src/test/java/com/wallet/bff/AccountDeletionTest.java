package com.wallet.bff;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.wallet.bff.cache.ScreenCache;
import com.wallet.bff.config.GatewayKey;
import com.wallet.bff.service.SessionDelivery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DELETE /api/me: the core checks the password and deletes the account, the BFF ends the session
 * and forgets the user's screens.
 *
 * <p>The usual read timeout is 5 s, not shorter, because the JWKS fetch that validates the first token
 * of the context uses it too: at 500 ms it timed out whenever the machine was busy and failed the
 * whole class. The slow-call test pays for it with delays past 5 s (and the slow timeout above them).
 */
@SpringBootTest(properties = {"wallet.rate-limit.auth-limit=1000", "wallet.core.read-timeout=5s",
        "wallet.core.slow-read-timeout=15s"})
class AccountDeletionTest extends BffTestSupport {

    private static final String PASSWORD_BODY = "{\"password\":\"password123\"}";

    /** Past the usual read timeout (5 s), inside the slow one (15 s). */
    private static final int CORE_DELAY_MS = 6000;

    @Autowired
    private ScreenCache cache;

    @Test
    void deletesInTheCoreEndsTheSessionAndForgetsTheScreens() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = token(userId);
        CORE.stubFor(WireMock.delete(urlEqualTo("/internal/me")).willReturn(aResponse().withStatus(204)));
        cache.get(userId, "home", "", () -> "before");

        mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(PASSWORD_BODY))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(SessionDelivery.REFRESH_COOKIE + "="),
                        containsString("Max-Age=0"),
                        containsString("Path=/api/auth"))));

        CORE.verify(deleteRequestedFor(urlEqualTo("/internal/me"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + token))
                .withHeader(GatewayKey.HEADER, equalTo(GATEWAY_KEY))
                .withRequestBody(equalToJson(PASSWORD_BODY)));
        assertThat(cache.get(userId, "home", "", () -> "after")).isEqualTo("after");
    }

    /** The core revokes each connection at Pluggy before it answers: the BFF waits past its usual timeout. */
    @Test
    void waitsForACoreThatIsRevokingConnections() throws Exception {
        UUID userId = UUID.randomUUID();
        CORE.stubFor(WireMock.delete(urlEqualTo("/internal/me")).willReturn(aResponse().withStatus(204).withFixedDelay(CORE_DELAY_MS)));
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/me")).willReturn(aResponse().withStatus(200).withFixedDelay(CORE_DELAY_MS)));

        mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(PASSWORD_BODY))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("bff.core_unavailable"));
    }

    @Test
    void aWrongPasswordIsRelayedAndTheSessionStays() throws Exception {
        UUID userId = UUID.randomUUID();
        CORE.stubFor(WireMock.delete(urlEqualTo("/internal/me")).willReturn(aResponse().withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"auth.wrong_password\",\"message\":\"The password does not match\"}")));

        mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.wrong_password"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void whenTheCoreRefusesNothingChangesHere() throws Exception {
        UUID userId = UUID.randomUUID();
        CORE.stubFor(WireMock.delete(urlEqualTo("/internal/me")).willReturn(aResponse().withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"internal.error\",\"message\":\"Unexpected error\"}")));
        cache.get(userId, "home", "", () -> "before");

        mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(PASSWORD_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("internal.error"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(cache.get(userId, "home", "", () -> "after")).isEqualTo("before");
    }

    @Test
    void withoutATokenNothingReachesTheCore() throws Exception {
        mockMvc.perform(delete("/api/me").contentType(MediaType.APPLICATION_JSON).content(PASSWORD_BODY))
                .andExpect(status().isUnauthorized());

        CORE.verify(0, deleteRequestedFor(urlEqualTo("/internal/me")));
    }
}
