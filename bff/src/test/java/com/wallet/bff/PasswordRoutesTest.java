package com.wallet.bff;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.wallet.bff.config.ClientAddressPropagationInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Esqueci minha senha", "Trocar senha" and the new recovery codes: relayed as they come (the core
 * checks the passwords and the codes), plus the page the reset link opens.
 */
@SpringBootTest(properties = "wallet.rate-limit.auth-limit=1000")
class PasswordRoutesTest extends BffTestSupport {

    private static final String ADDRESS = "203.0.113.30";

    @Test
    void forgotPasswordAnswers202WithNoBodyAndNeedsNoSession() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/password/forgot")).willReturn(aResponse().withStatus(202)));

        perform(post("/api/auth/password/forgot"), "{\"email\":\"rafael@example.com\"}")
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        CORE.verify(postRequestedFor(urlEqualTo("/internal/auth/password/forgot"))
                .withHeader(HttpHeaders.AUTHORIZATION, absent())
                .withRequestBody(equalToJson("{\"email\":\"rafael@example.com\"}")));
    }

    /** The core scopes the cleared lockout to this address. */
    @Test
    void theResetIsRelayedWithTheTokenThePasswordAndWhereItCameFrom() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/password/reset")).willReturn(aResponse().withStatus(204)));

        perform(post("/api/auth/password/reset"), "{\"token\":\"abc\",\"newPassword\":\"a brand new password\"}")
                .andExpect(status().isNoContent());

        CORE.verify(postRequestedFor(urlEqualTo("/internal/auth/password/reset"))
                .withHeader(HttpHeaders.AUTHORIZATION, absent())
                .withHeader(ClientAddressPropagationInterceptor.HEADER, equalTo(ADDRESS))
                .withRequestBody(equalToJson("{\"token\":\"abc\",\"newPassword\":\"a brand new password\"}")));
    }

    @Test
    void aGoneLinkIsTheCoresAnswer() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/password/reset")).willReturn(aResponse().withStatus(422)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"auth.link_invalid\",\"message\":\"gone\"}")));

        perform(post("/api/auth/password/reset"), "{\"token\":\"old\",\"newPassword\":\"a brand new password\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("auth.link_invalid"));
    }

    @Test
    void theResetPageIsServedLockedDown() throws Exception {
        mockMvc.perform(get("/password/reset"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/password/reset.html"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        mockMvc.perform(get("/password/reset.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nova senha")))
                .andExpect(content().string(containsString("Repita a nova senha")))
                .andExpect(content().string(containsString("Trocar senha")))
                .andExpect(content().string(containsString("/password/reset.js")));
        mockMvc.perform(get("/password/reset.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/auth/password/reset")));
    }

    @Test
    void changingThePasswordCarriesBothPasswordsAndTheSession() throws Exception {
        String bearer = bearer(UUID.randomUUID());
        CORE.stubFor(WireMock.put(urlEqualTo("/internal/me/password")).willReturn(aResponse().withStatus(204)));

        perform(put("/api/me/password").header(HttpHeaders.AUTHORIZATION, bearer),
                "{\"currentPassword\":\"old password\",\"newPassword\":\"a brand new password\"}")
                .andExpect(status().isNoContent());

        CORE.verify(putRequestedFor(urlEqualTo("/internal/me/password"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bearer))
                .withHeader(ClientAddressPropagationInterceptor.HEADER, equalTo(ADDRESS))
                .withRequestBody(equalToJson("{\"currentPassword\":\"old password\",\"newPassword\":\"a brand new password\"}")));
    }

    @Test
    void theCoresRefusalsOfAPasswordChangeTravelOn() throws Exception {
        CORE.stubFor(WireMock.put(urlEqualTo("/internal/me/password")).willReturn(aResponse().withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"auth.wrong_password\",\"message\":\"no\"}")));

        perform(put("/api/me/password").header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())),
                "{\"currentPassword\":\"guess\",\"newPassword\":\"a brand new password\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.wrong_password"));
    }

    @Test
    void newRecoveryCodesComeBackOnce() throws Exception {
        String bearer = bearer(UUID.randomUUID());
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/me/two-factor/recovery-codes"))
                .willReturn(okJson("{\"recoveryCodes\":[\"AAAA-BBBB-CCCC-DDDD\"]}")));

        perform(post("/api/me/two-factor/recovery-codes").header(HttpHeaders.AUTHORIZATION, bearer),
                "{\"password\":\"p\",\"code\":\"123456\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes[0]").value("AAAA-BBBB-CCCC-DDDD"));

        CORE.verify(postRequestedFor(urlEqualTo("/internal/me/two-factor/recovery-codes"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bearer))
                .withRequestBody(equalToJson("{\"password\":\"p\",\"code\":\"123456\"}")));
    }

    @Test
    void theSessionRoutesNeedASession() throws Exception {
        perform(put("/api/me/password"), "{\"currentPassword\":\"p\",\"newPassword\":\"a brand new password\"}")
                .andExpect(status().isUnauthorized());
        perform(post("/api/me/two-factor/recovery-codes"), "{\"password\":\"p\",\"code\":\"123456\"}")
                .andExpect(status().isUnauthorized());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request
                .with(servletRequest -> {
                    servletRequest.setRemoteAddr(ADDRESS);
                    return servletRequest;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
