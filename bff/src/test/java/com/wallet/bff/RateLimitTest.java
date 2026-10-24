package com.wallet.bff;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.wallet.bff.ratelimit.IpPseudonyms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Its own context: tiny limits that would trip every other test. */
@SpringBootTest(properties = {"wallet.rate-limit.default-limit=3", "wallet.rate-limit.auth-limit=2",
        "wallet.rate-limit.link-limit=2", "wallet.rate-limit.connect-token-limit=1"})
@ExtendWith(OutputCaptureExtension.class)
class RateLimitTest extends BffTestSupport {

    @Autowired
    private IpPseudonyms pseudonyms;

    private static final String CONNECTION = """
            {"id":"6f1c9a52-3b7e-4d1a-9c2f-8e5d4b3a2f10","institutionName":"Nubank","institutionImageUrl":null,
             "status":"ACTIVE","lastSyncedAt":null,"consentExpiresAt":null,"createdAt":"2026-10-21T12:00:00Z"}""";

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

    /** Password guessing from one address hits the wall before it reaches the core. */
    @Test
    void loginAttemptsAreLimitedPerAddress() throws Exception {
        stubLogin();

        login("203.0.113.7").andExpect(status().isOk());
        login("203.0.113.7").andExpect(status().isOk());
        login("203.0.113.7")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limit.exceeded"));
        login("203.0.113.8").andExpect(status().isOk());

        CORE.verify(3, postRequestedFor(urlEqualTo("/internal/auth/login")));
    }

    /**
     * An address is personal data: the log names the source by a stable pseudonym, so an abuse still
     * reads as one source across lines, and never by the address itself (L12).
     */
    @Test
    void theLogNamesAnAddressByItsPseudonymNeverByTheAddress(CapturedOutput output) throws Exception {
        stubLogin();

        login("203.0.113.40").andExpect(status().isOk());
        login("203.0.113.40").andExpect(status().isOk());
        login("203.0.113.40").andExpect(status().isTooManyRequests());
        login("203.0.113.40").andExpect(status().isTooManyRequests());

        String label = "[RATE-LIMIT] auth:ip:" + pseudonyms.of("203.0.113.40") + " went over 2 requests per PT1M";
        assertThat(output.getAll()).contains(label).doesNotContain("203.0.113.40");
        assertThat(output.getAll().split(java.util.regex.Pattern.quote(label), -1)).hasSize(3);
    }

    /**
     * Each link makes the core read up to a year of the bank at Pluggy: a low budget of its own,
     * per user and over an hour, that a percent-encoded path does not get around.
     */
    @Test
    void linkingHasABudgetOfItsOwn() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/connections")).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json").withBody(CONNECTION)));
        String rafael = bearer(UUID.randomUUID());

        link(rafael, "/api/connections").andExpect(status().isCreated());
        link(rafael, "/api/connections").andExpect(status().isCreated());
        link(rafael, "/api/%63onnections")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "3600"))
                .andExpect(jsonPath("$.code").value("rate_limit.connections"));
        link(bearer(UUID.randomUUID()), "/api/connections").andExpect(status().isCreated());

        CORE.verify(3, postRequestedFor(urlEqualTo("/internal/connections")));
    }

    /** Every widget token is a call to Pluggy; spending them does not block the rest of the app. */
    @Test
    void connectTokensHaveABudgetOfTheirOwn() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/connections/connect-token"))
                .willReturn(okJson("{\"connectToken\":\"connect-token-1\"}")));
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/me")).willReturn(okJson("{\"id\":\"%s\"}".formatted(UUID.randomUUID()))));
        String rafael = bearer(UUID.randomUUID());

        connectToken(rafael).andExpect(status().isOk());
        connectToken(rafael)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limit.connections"));
        me(rafael).andExpect(status().isOk());
        connectToken(bearer(UUID.randomUUID())).andExpect(status().isOk());

        CORE.verify(2, postRequestedFor(urlEqualTo("/internal/connections/connect-token")));
    }

    /** Without trusted proxies, writing X-Forwarded-For does not buy a fresh budget. */
    /** An open session is no way around the sign-in limit to guess the password. */
    @Test
    void routesThatCheckThePasswordAgainHaveTheSignInLimitPerUser() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/me/two-factor/setup")).willReturn(aResponse().withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"auth.wrong_password\",\"message\":\"no\"}")));
        String guesser = bearer(UUID.randomUUID());

        twoFactorSetUp(guesser).andExpect(status().isForbidden());
        twoFactorSetUp(guesser).andExpect(status().isForbidden());
        twoFactorSetUp(guesser)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limit.exceeded"));

        twoFactorSetUp(bearer(UUID.randomUUID())).andExpect(status().isForbidden());
        CORE.verify(3, postRequestedFor(urlEqualTo("/internal/me/two-factor/setup")));
    }

    @Test
    void aForgedForwardedForDoesNotEscapeTheLimit() throws Exception {
        stubLogin();

        login("203.0.113.9", "198.51.100.1").andExpect(status().isOk());
        login("203.0.113.9", "198.51.100.2").andExpect(status().isOk());
        login("203.0.113.9", "198.51.100.3")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limit.exceeded"));
    }

    /** Changing the password and renewing the recovery codes ask for the password again: same budget as the others. */
    @Test
    void thePasswordChangeAndTheNewRecoveryCodesHaveTheSignInLimitPerUser() throws Exception {
        CORE.stubFor(WireMock.put(urlEqualTo("/internal/me/password")).willReturn(aResponse().withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"auth.wrong_password\",\"message\":\"no\"}")));
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/me/two-factor/recovery-codes")).willReturn(aResponse().withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"auth.wrong_password\",\"message\":\"no\"}")));
        String guesser = bearer(UUID.randomUUID());

        changePassword(guesser).andExpect(status().isForbidden());
        newRecoveryCodes(guesser).andExpect(status().isForbidden());
        changePassword(guesser)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limit.exceeded"));

        changePassword(bearer(UUID.randomUUID())).andExpect(status().isForbidden());
        CORE.verify(2, putRequestedFor(urlEqualTo("/internal/me/password")));
    }

    private ResultActions changePassword(String bearer) throws Exception {
        return mockMvc.perform(put("/api/me/password").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"guess\",\"newPassword\":\"a brand new password\"}"));
    }

    private ResultActions newRecoveryCodes(String bearer) throws Exception {
        return mockMvc.perform(post("/api/me/two-factor/recovery-codes").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"guess\",\"code\":\"123456\"}"));
    }

    private ResultActions login(String address, String forwardedFor) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .header("X-Forwarded-For", forwardedFor)
                .header("X-Wallet-Client", "mobile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"rafael@example.com\",\"password\":\"secret-password\"}"));
    }

    static void stubLogin() {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/login")).willReturn(okJson(
                "{\"accessToken\":\"a\",\"tokenType\":\"Bearer\",\"expiresIn\":900,\"refreshToken\":\"r\","
                        + "\"refreshTokenExpiresAt\":\"%s\"}".formatted(Instant.now().plusSeconds(3600)))));
    }

    private ResultActions link(String bearer, String path) throws Exception {
        return mockMvc.perform(post(URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerItemId\":\"%s\"}".formatted(UUID.randomUUID())));
    }

    private ResultActions connectToken(String bearer) throws Exception {
        return mockMvc.perform(post("/api/connections/connect-token").header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions twoFactorSetUp(String bearer) throws Exception {
        return mockMvc.perform(post("/api/me/two-factor/setup").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"guess\"}"));
    }

    private ResultActions me(String bearer) throws Exception {
        return mockMvc.perform(get("/api/me")
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions login(String address) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .header("X-Wallet-Client", "mobile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"rafael@example.com\",\"password\":\"secret-password\"}"));
    }
}
