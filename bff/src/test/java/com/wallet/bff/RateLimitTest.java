package com.wallet.bff;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    /** Password guessing from one address hits the wall before it reaches the core. */
    @Test
    void loginAttemptsAreLimitedPerAddress() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/login")).willReturn(okJson(
                "{\"accessToken\":\"a\",\"tokenType\":\"Bearer\",\"expiresIn\":900,\"refreshToken\":\"r\","
                        + "\"refreshTokenExpiresAt\":\"%s\"}".formatted(Instant.now().plusSeconds(3600)))));

        login("203.0.113.7").andExpect(status().isOk());
        login("203.0.113.7").andExpect(status().isOk());
        login("203.0.113.7")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limit.exceeded"));
        login("203.0.113.8").andExpect(status().isOk());

        CORE.verify(3, postRequestedFor(urlEqualTo("/internal/auth/login")));
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
