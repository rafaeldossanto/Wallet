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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The account routes: sign-up with e-mail confirmation, the confirmation page, the profile and the
 * second factor. The BFF relays them as they come and tells the core where each request came from.
 */
@SpringBootTest(properties = "wallet.rate-limit.auth-limit=1000")
class AccountRoutesTest extends BffTestSupport {

    private static final String ADDRESS = "203.0.113.20";

    @Test
    void signUpAnswersAcceptedWithNoBody() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/register")).willReturn(aResponse().withStatus(202)));
        String body = "{\"email\":\"rafael@example.com\",\"password\":\"password123\",\"displayName\":\"Rafael\",\"acceptTerms\":true}";

        perform(post("/api/auth/register"), body)
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        CORE.verify(postRequestedFor(urlEqualTo("/internal/auth/register")).withRequestBody(equalToJson(body)));
    }

    /** The core scopes its lockout to this address: one place guessing does not lock the owner out everywhere. */
    @Test
    void signInCarriesTheCodeAndWhereTheRequestCameFrom() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/login")).willReturn(okJson("""
                {"accessToken":"a","tokenType":"Bearer","expiresIn":900,"refreshToken":"r","refreshTokenExpiresAt":"2026-11-01T12:00:00Z"}
                """)));

        perform(post("/api/auth/login").header("X-Wallet-Client", "mobile"),
                "{\"email\":\"rafael@example.com\",\"password\":\"password123\",\"code\":\"123456\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("r"));

        CORE.verify(postRequestedFor(urlEqualTo("/internal/auth/login"))
                .withHeader(ClientAddressPropagationInterceptor.HEADER, equalTo(ADDRESS))
                .withRequestBody(equalToJson("{\"email\":\"rafael@example.com\",\"password\":\"password123\",\"code\":\"123456\"}")));
    }

    @Test
    void aSignInWithoutACodeSendsNone() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/login")).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"auth.two_factor_required\",\"message\":\"Send the code\"}")));

        perform(post("/api/auth/login").header("X-Wallet-Client", "web"),
                "{\"email\":\"rafael@example.com\",\"password\":\"password123\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.two_factor_required"));

        CORE.verify(postRequestedFor(urlEqualTo("/internal/auth/login"))
                .withRequestBody(equalToJson("{\"email\":\"rafael@example.com\",\"password\":\"password123\"}")));
    }

    @Test
    void theConfirmationPageIsServedLockedDown() throws Exception {
        mockMvc.perform(get("/email/confirm"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/email/confirm.html"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        mockMvc.perform(get("/email/confirm.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Confirmar e-mail")))
                .andExpect(content().string(containsString("/email/confirm.js")));
        mockMvc.perform(get("/email/confirm.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/auth/email/confirm")));
    }

    /** What the page posts: no session, no client header, only the token of the link. */
    @Test
    void theLinkIsConfirmedThroughTheCore() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/email/confirm"))
                .willReturn(okJson("{\"result\":\"ACCOUNT_CONFIRMED\"}")));

        perform(post("/api/auth/email/confirm"), "{\"token\":\"abc\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ACCOUNT_CONFIRMED"));

        CORE.verify(postRequestedFor(urlEqualTo("/internal/auth/email/confirm"))
                .withHeader(HttpHeaders.AUTHORIZATION, absent())
                .withRequestBody(equalToJson("{\"token\":\"abc\"}")));
    }

    @Test
    void aGoneLinkIsTheCoresAnswer() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/email/confirm")).willReturn(aResponse().withStatus(422)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"auth.link_invalid\",\"message\":\"gone\"}")));

        perform(post("/api/auth/email/confirm"), "{\"token\":\"old\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("auth.link_invalid"));
    }

    @Test
    void anotherLinkCanBeAskedFor() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/email/resend")).willReturn(aResponse().withStatus(202)));

        perform(post("/api/auth/email/resend"), "{\"email\":\"rafael@example.com\"}").andExpect(status().isAccepted());

        CORE.verify(postRequestedFor(urlEqualTo("/internal/auth/email/resend"))
                .withRequestBody(equalToJson("{\"email\":\"rafael@example.com\"}")));
    }

    @Test
    void renamesTheAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        CORE.stubFor(WireMock.put(urlEqualTo("/internal/me")).willReturn(okJson("""
                {"id":"%s","email":"rafael@example.com","displayName":"Rafael Santos","createdAt":"2026-09-01T12:00:00Z",
                 "pendingEmail":null,"twoFactorEnabled":false}
                """.formatted(userId))));

        perform(put("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(userId)), "{\"displayName\":\"Rafael Santos\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Rafael Santos"))
                .andExpect(jsonPath("$.twoFactorEnabled").value(false));

        CORE.verify(putRequestedFor(urlEqualTo("/internal/me")).withRequestBody(equalToJson("{\"displayName\":\"Rafael Santos\"}")));
    }

    /** The box of the terms goes to the core as it came: the core is the one that refuses a sign-up without it (L15). */
    @Test
    void theAcceptanceOfTheTermsTravelsWithTheSignUp() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/register")).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"validation.failed\",\"message\":\"Request has invalid fields\"}")));

        perform(post("/api/auth/register"), "{\"email\":\"rafael@example.com\",\"password\":\"password123\",\"displayName\":\"Rafael\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        CORE.verify(postRequestedFor(urlEqualTo("/internal/auth/register"))
                .withRequestBody(equalToJson("{\"email\":\"rafael@example.com\",\"password\":\"password123\",\"displayName\":\"Rafael\"}", true, true)));
    }

    @Test
    void theProfileSaysWhetherTheTermsMustBeAcceptedAgain() throws Exception {
        UUID userId = UUID.randomUUID();
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/me")).willReturn(okJson("""
                {"id":"%s","email":"rafael@example.com","displayName":"Rafael","createdAt":"2026-09-01T12:00:00Z",
                 "pendingEmail":null,"twoFactorEnabled":false,"termsAcceptanceRequired":true,
                 "termsVersion":"2026-10-preliminar","termsUrl":"https://wallet.example.com/termos.html",
                 "privacyUrl":"https://wallet.example.com/privacidade.html"}
                """.formatted(userId))));

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termsAcceptanceRequired").value(true))
                .andExpect(jsonPath("$.termsVersion").value("2026-10-preliminar"))
                .andExpect(jsonPath("$.termsUrl").value("https://wallet.example.com/termos.html"))
                .andExpect(jsonPath("$.privacyUrl").value("https://wallet.example.com/privacidade.html"));
    }

    @Test
    void theAcceptanceOfTheTermsIsRelayedWithItsVersion() throws Exception {
        UUID userId = UUID.randomUUID();
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/me/terms")).willReturn(aResponse().withStatus(204)));
        String body = "{\"version\":\"2026-10-preliminar\"}";

        perform(post("/api/me/terms").header(HttpHeaders.AUTHORIZATION, bearer(userId)), body)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        CORE.verify(postRequestedFor(urlEqualTo("/internal/me/terms"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bearer(userId)))
                .withRequestBody(equalToJson(body)));
    }

    @Test
    void anOutdatedVersionIsTheCoresAnswer() throws Exception {
        UUID userId = UUID.randomUUID();
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/me/terms")).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"legal.terms_outdated\",\"message\":\"changed\"}")));

        perform(post("/api/me/terms").header(HttpHeaders.AUTHORIZATION, bearer(userId)), "{\"version\":\"2020-old\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("legal.terms_outdated"));
    }

    @Test
    void withoutASessionTheTermsAreNotAccepted() throws Exception {
        perform(post("/api/me/terms"), "{\"version\":\"2026-10-preliminar\"}").andExpect(status().isUnauthorized());

        CORE.verify(0, postRequestedFor(urlEqualTo("/internal/me/terms")));
    }

    @Test
    void anEmailChangeGoesWithThePasswordAndTheAddress() throws Exception {
        UUID userId = UUID.randomUUID();
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/me/email")).willReturn(aResponse().withStatus(202)));
        String body = "{\"newEmail\":\"novo@example.com\",\"password\":\"password123\"}";

        perform(post("/api/me/email").header(HttpHeaders.AUTHORIZATION, bearer(userId)), body)
                .andExpect(status().isAccepted());

        CORE.verify(postRequestedFor(urlEqualTo("/internal/me/email"))
                .withHeader(ClientAddressPropagationInterceptor.HEADER, equalTo(ADDRESS))
                .withRequestBody(equalToJson(body)));
    }

    @Test
    void theSecondFactorIsRelayed() throws Exception {
        UUID userId = UUID.randomUUID();
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/me/two-factor/setup")).willReturn(okJson(
                "{\"secret\":\"JBSWY3DPEHPK3PXP\",\"otpauthUri\":\"otpauth://totp/Wallet:r?secret=JBSWY3DPEHPK3PXP\"}")));
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/me/two-factor/enable")).willReturn(okJson(
                "{\"recoveryCodes\":[\"ABCD-EFGH-JKMN-PQRS\"]}")));
        CORE.stubFor(WireMock.delete(urlEqualTo("/internal/me/two-factor")).willReturn(aResponse().withStatus(204)));

        perform(post("/api/me/two-factor/setup").header(HttpHeaders.AUTHORIZATION, bearer(userId)), "{\"password\":\"p\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpauthUri").value("otpauth://totp/Wallet:r?secret=JBSWY3DPEHPK3PXP"));
        perform(post("/api/me/two-factor/enable").header(HttpHeaders.AUTHORIZATION, bearer(userId)), "{\"code\":\"123456\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes[0]").value("ABCD-EFGH-JKMN-PQRS"));
        perform(delete("/api/me/two-factor").header(HttpHeaders.AUTHORIZATION, bearer(userId)),
                "{\"password\":\"p\",\"code\":\"123456\"}")
                .andExpect(status().isNoContent());

        CORE.verify(deleteRequestedFor(urlEqualTo("/internal/me/two-factor"))
                .withHeader(ClientAddressPropagationInterceptor.HEADER, equalTo(ADDRESS))
                .withRequestBody(equalToJson("{\"password\":\"p\",\"code\":\"123456\"}")));
    }

    @Test
    void aWrongCodeIsTheCoresAnswerAndTheSessionStays() throws Exception {
        UUID userId = UUID.randomUUID();
        CORE.stubFor(WireMock.delete(urlEqualTo("/internal/me/two-factor")).willReturn(aResponse().withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"auth.invalid_two_factor_code\",\"message\":\"no\"}")));

        perform(delete("/api/me/two-factor").header(HttpHeaders.AUTHORIZATION, bearer(userId)),
                "{\"password\":\"p\",\"code\":\"000000\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.invalid_two_factor_code"));
    }

    @Test
    void withoutASessionTheProfileIsNotReached() throws Exception {
        perform(post("/api/me/two-factor/setup"), "{\"password\":\"p\"}").andExpect(status().isUnauthorized());
        perform(put("/api/me"), "{\"displayName\":\"x\"}").andExpect(status().isUnauthorized());

        CORE.verify(0, postRequestedFor(urlEqualTo("/internal/me/two-factor/setup")));
        CORE.verify(0, putRequestedFor(urlEqualTo("/internal/me")));
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
