package com.wallet.bff;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.springframework.test.web.servlet.ResultActions;
import com.github.tomakehurst.wiremock.http.Fault;
import com.wallet.bff.config.GatewayKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The edge itself: tokens, trace ids, core errors, the circuit breaker and CORS. */
@SpringBootTest(properties = "wallet.rate-limit.auth-limit=1000")
class BffEdgeTest extends BffTestSupport {

    private static final String USER_JSON =
            "{\"id\":\"%s\",\"email\":\"rafael@example.com\",\"displayName\":\"Rafael\",\"createdAt\":\"2026-10-01T12:00:00Z\"}";

    @Test
    void onlyTheLivenessProbeIsPublic() throws Exception {
        mockMvc.perform(get("/livez"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true));
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutATokenNothingReachesTheCore() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthenticated"));

        CORE.verify(0, getRequestedFor(urlEqualTo("/internal/me")));
    }

    @Test
    void forwardsTheSameTokenAndTraceIdToTheCore() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = token(userId);
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/me")).willReturn(okJson(USER_JSON.formatted(userId))));

        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Trace-Id", "trace-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-123"))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("rafael@example.com"));

        CORE.verify(getRequestedFor(urlEqualTo("/internal/me"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + token))
                .withHeader("X-Trace-Id", equalTo("trace-123"))
                .withHeader(GatewayKey.HEADER, equalTo(GATEWAY_KEY)));
    }

    /** The core refuses the login route itself without the key, so the public routes carry it too. */
    @Test
    void publicRoutesAlsoCarryTheGatewayKey() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/register")).willReturn(aResponse().withStatus(202)));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"password\":\"password123\",\"displayName\":\"New\"}"))
                .andExpect(status().isAccepted());

        CORE.verify(postRequestedFor(urlEqualTo("/internal/auth/register"))
                .withHeader(GatewayKey.HEADER, equalTo(GATEWAY_KEY))
                .withoutHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void replacesATraceIdThatCouldForgeLogLines() throws Exception {
        mockMvc.perform(get("/livez")
                        .header("X-Trace-Id", "not safe; rm -rf"))
                .andExpect(header().string("X-Trace-Id", matchesPattern("[0-9a-f]{8}")));
    }

    @Test
    void rejectsTokensSignedByAnotherKeyExpiredOrFromAnotherIssuer() throws Exception {
        UUID userId = UUID.randomUUID();
        String foreign = token(userId, Instant.now().plusSeconds(600), "wallet-core", generateKey("wallet-core-1"));
        String expired = token(userId, Instant.now().minusSeconds(600), "wallet-core", SIGNING_KEY);
        String otherIssuer = token(userId, Instant.now().plusSeconds(600), "somebody-else", SIGNING_KEY);

        for (String token : new String[]{foreign, expired, otherIssuer}) {
            mockMvc.perform(get("/api/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        CORE.verify(0, getRequestedFor(urlEqualTo("/internal/me")));
    }

    /** Signed by the core, but without the Wallet API's audience (issued before it) or with another one. */
    @Test
    void rejectsTokensWithoutTheWalletApiAudience() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant later = Instant.now().plusSeconds(600);
        String withoutAudience = token(userId, later, "wallet-core", SIGNING_KEY, null);
        String otherAudience = token(userId, later, "wallet-core", SIGNING_KEY, "some-other-service");

        for (String token : new String[]{withoutAudience, otherAudience}) {
            mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("auth.unauthenticated"));
        }
        CORE.verify(0, getRequestedFor(urlEqualTo("/internal/me")));
    }

    @Test
    void coreErrorsReachTheAppUntouched() throws Exception {
        String coreError = "{\"code\":\"auth.unauthenticated\",\"message\":\"User of this token no longer exists\"}";
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/me"))
                .willReturn(aResponse().withStatus(401).withHeader("Content-Type", "application/json").withBody(coreError)));

        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(coreError));
    }

    @Test
    void coreThatDoesNotAnswerIsUnavailable() throws Exception {
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/me")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("bff.core_unavailable"));
    }

    /** The JDK client may retry a GET once on a reset connection, so the count is compared, not fixed. */
    @Test
    void openCircuitStopsCallingTheCore() throws Exception {
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/me")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        String bearer = bearer(UUID.randomUUID());

        for (int attempt = 0; attempt < 10; attempt++) {
            callMe(bearer).andExpect(status().isServiceUnavailable());
        }
        int reachedTheCore = coreCallsToMe();
        for (int attempt = 0; attempt < 5; attempt++) {
            callMe(bearer)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("bff.core_unavailable"));
        }

        assertThat(reachedTheCore).isPositive();
        assertThat(coreCallsToMe()).isEqualTo(reachedTheCore);
    }

    private ResultActions callMe(String bearer) throws Exception {
        return mockMvc.perform(get("/api/me")
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private static int coreCallsToMe() {
        return CORE.countRequestsMatching(getRequestedFor(urlEqualTo("/internal/me")).build()).getCount();
    }

    @Test
    void corsLetsTheWebAppSendItsCookie() throws Exception {
        mockMvc.perform(options("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Wallet-Client"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }
}
