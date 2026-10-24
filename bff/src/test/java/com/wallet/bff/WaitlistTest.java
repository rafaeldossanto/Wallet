package com.wallet.bff;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The waitlist routes of the landing page: public, no token, relayed to the core. Every test posts
 * from an address of its own, because the limit (5 an hour per address) is the one the production
 * default sets and the context is shared.
 */
@SpringBootTest
class WaitlistTest extends BffTestSupport {

    private static final String SUBSCRIBE = "/internal/waitlist";
    private static final String CONFIRM = "/internal/waitlist/confirm";
    private static final String UNSUBSCRIBE = "/internal/waitlist/unsubscribe";

    @Test
    void aSubscriptionIsRelayedToTheCoreWithoutTheConsent() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo(SUBSCRIBE)).willReturn(aResponse().withStatus(202)));

        json(post("/api/waitlist"), "198.51.100.1", "{\"email\":\"rafael@example.com\",\"consent\":true}")
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        CORE.verify(postRequestedFor(urlEqualTo(SUBSCRIBE))
                .withHeader(HttpHeaders.AUTHORIZATION, absent())
                .withRequestBody(equalToJson("{\"email\":\"rafael@example.com\"}")));
    }

    @Test
    void withoutTheConsentNothingReachesTheCore() throws Exception {
        json(post("/api/waitlist"), "198.51.100.2", "{\"email\":\"rafael@example.com\",\"consent\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
        json(post("/api/waitlist"), "198.51.100.2", "{\"email\":\"rafael@example.com\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        CORE.verify(0, postRequestedFor(urlEqualTo(SUBSCRIBE)));
    }

    @Test
    void anInvalidEmailNeverReachesTheCore() throws Exception {
        for (String email : new String[]{"", "not-an-email", "a".repeat(320) + "@example.com"}) {
            json(post("/api/waitlist"), "198.51.100.3", "{\"email\":\"%s\",\"consent\":true}".formatted(email))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation.failed"));
        }
        json(post("/api/waitlist"), "198.51.100.3", "{\"consent\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        CORE.verify(0, postRequestedFor(urlEqualTo(SUBSCRIBE)));
    }

    @Test
    void theConfirmationIsRelayed() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo(CONFIRM)).willReturn(aResponse().withStatus(204)));

        json(post("/api/waitlist/confirm"), "198.51.100.4", "{\"token\":\"abc\"}")
                .andExpect(status().isNoContent());

        CORE.verify(postRequestedFor(urlEqualTo(CONFIRM)).withRequestBody(equalToJson("{\"token\":\"abc\"}")));
    }

    @Test
    void aGoneLinkIsTheCoresAnswer() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo(CONFIRM)).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"waitlist.invalid_token\",\"message\":\"gone\"}")));

        json(post("/api/waitlist/confirm"), "198.51.100.5", "{\"token\":\"old\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("waitlist.invalid_token"));
    }

    /** What the page posts: the token of the link, in the query. */
    @Test
    void theUnsubscribePageSendsTheTokenInTheQuery() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo(UNSUBSCRIBE)).willReturn(aResponse().withStatus(204)));

        from(post("/api/waitlist/unsubscribe").queryParam("token", "abc"), "198.51.100.6")
                .andExpect(status().isNoContent());

        CORE.verify(postRequestedFor(urlEqualTo(UNSUBSCRIBE)).withRequestBody(equalToJson("{\"token\":\"abc\"}")));
    }

    @Test
    void theTokenMayComeInTheJsonBody() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo(UNSUBSCRIBE)).willReturn(aResponse().withStatus(204)));

        json(post("/api/waitlist/unsubscribe"), "198.51.100.7", "{\"token\":\"abc\"}")
                .andExpect(status().isNoContent());

        CORE.verify(postRequestedFor(urlEqualTo(UNSUBSCRIBE)).withRequestBody(equalToJson("{\"token\":\"abc\"}")));
    }

    /** RFC 8058: the mail provider posts a form with {@code List-Unsubscribe=One-Click} to the exact URL of the e-mail. */
    @Test
    void theOneClickPostOfTheMailProviderUnsubscribes() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo(UNSUBSCRIBE)).willReturn(aResponse().withStatus(204)));

        from(post("/api/waitlist/unsubscribe").queryParam("token", "abc")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("List-Unsubscribe=One-Click"), "198.51.100.8")
                .andExpect(status().isNoContent());

        CORE.verify(postRequestedFor(urlEqualTo(UNSUBSCRIBE)).withRequestBody(equalToJson("{\"token\":\"abc\"}")));
    }

    @Test
    void fiveRequestsAnHourPerAddress() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo(SUBSCRIBE)).willReturn(aResponse().withStatus(202)));
        CORE.stubFor(WireMock.post(urlEqualTo(UNSUBSCRIBE)).willReturn(aResponse().withStatus(204)));
        String body = "{\"email\":\"rafael@example.com\",\"consent\":true}";

        for (int attempt = 0; attempt < 4; attempt++) {
            json(post("/api/waitlist"), "198.51.100.9", body).andExpect(status().isAccepted());
        }
        from(post("/api/waitlist/unsubscribe").queryParam("token", "abc"), "198.51.100.9")
                .andExpect(status().isNoContent());
        json(post("/api/waitlist"), "198.51.100.9", body)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "3600"))
                .andExpect(jsonPath("$.code").value("rate_limit.exceeded"));

        json(post("/api/waitlist"), "198.51.100.10", body).andExpect(status().isAccepted());
        CORE.verify(5, postRequestedFor(urlEqualTo(SUBSCRIBE)));
    }

    @Test
    void theConfirmationPageIsServedLockedDown() throws Exception {
        mockMvc.perform(get("/waitlist/confirm"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/waitlist/confirm.html"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        mockMvc.perform(get("/waitlist/confirm.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Confirmar meu e-mail")))
                .andExpect(content().string(containsString("/waitlist/confirm.js")));
        mockMvc.perform(get("/waitlist/confirm.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/waitlist/confirm")));
    }

    /** A GET (a mail scanner opening the link) unsubscribes nothing: the page only does it when the button is pressed. */
    @Test
    void theUnsubscribePageIsServedAndOnlyTheButtonUnsubscribes() throws Exception {
        mockMvc.perform(get("/waitlist/unsubscribe").queryParam("token", "abc"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/waitlist/unsubscribe.html"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        mockMvc.perform(get("/waitlist/unsubscribe.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sair da lista")))
                .andExpect(content().string(containsString("/waitlist/unsubscribe.js")));
        mockMvc.perform(get("/waitlist/unsubscribe.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/waitlist/unsubscribe")));

        CORE.verify(0, postRequestedFor(urlEqualTo(UNSUBSCRIBE)));
    }

    /** The landing page is served from another origin, listed in WALLET_WEB_ALLOWED_ORIGINS. */
    @Test
    void theLandingPageOfAnAllowedOriginMayPost() throws Exception {
        mockMvc.perform(options("/api/waitlist")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5000"));
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String address, String body) throws Exception {
        return from(request.contentType(MediaType.APPLICATION_JSON).content(body), address);
    }

    private ResultActions from(MockHttpServletRequestBuilder request, String address) throws Exception {
        return mockMvc.perform(request.with(servletRequest -> {
            servletRequest.setRemoteAddr(address);
            return servletRequest;
        }));
    }
}
