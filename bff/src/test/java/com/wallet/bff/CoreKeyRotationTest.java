package com.wallet.bff;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The core gets a new key when it restarts without configured keys, and a new key id with it.
 * The BFF must pick it up on its own. Its own context (the property is only there to get one), so
 * the JWKS cache starts empty.
 */
@SpringBootTest(properties = "wallet.rate-limit.enabled=false")
class CoreKeyRotationTest extends BffTestSupport {

    @Test
    void aNewKeyIdInTheCoreIsFetchedWithoutRestartingTheBff() throws Exception {
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/me")).willReturn(okJson("{\"id\":\"%s\"}".formatted(UUID.randomUUID()))));
        me(token(UUID.randomUUID())).andExpect(status().isOk());

        RSAKey restarted = generateKey("after-restart");
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/.well-known/jwks.json"))
                .willReturn(okJson(new JWKSet(restarted.toPublicJWK()).toString())));
        String newToken = token(UUID.randomUUID(), Instant.now().plus(Duration.ofMinutes(15)), "wallet-core", restarted);

        me(newToken).andExpect(status().isOk());
    }

    private ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/api/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
