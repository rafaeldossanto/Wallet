package com.wallet.bff;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.wallet.bff.config.GatewayKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pluggy's webhook (finding A21): public, unsigned, so it is a hint. The BFF only lets in a call
 * that carries the secret (and comes from an allowed address, when a list is set), counts it per
 * address, and hands the body to the core untouched. Every test posts from an address of its own
 * inside the allowed range: the limit (5 a minute here) is per address and the context is shared.
 */
@SpringBootTest(properties = {
        "wallet.webhooks.enabled=true",
        "wallet.webhooks.secret=" + WebhookTest.SECRET,
        "wallet.webhooks.allowed-ips=198.51.100.0/24",
        "wallet.rate-limit.webhook-limit=5"})
class WebhookTest extends BffTestSupport {

    static final String SECRET = "a-webhook-secret-of-at-least-32-characters";
    private static final String HEADER = "X-Wallet-Webhook-Secret";
    private static final String PATH = "/api/webhooks/pluggy";
    private static final String CORE_PATH = "/internal/webhooks/pluggy";
    private static final String BODY = "{ \"event\" : \"item/updated\", \"eventId\":\"e-1\",\"itemId\":\"i-1\", \"extra\": [1, 2] }";

    @Test
    void theRightSecretIsAcceptedAndTheBodyGoesToTheCoreAsItCame() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo(CORE_PATH)).willReturn(aResponse().withStatus(202)));

        webhook("198.51.100.1", SECRET, BODY).andExpect(status().isAccepted()).andExpect(content().string(""));

        CORE.verify(postRequestedFor(urlEqualTo(CORE_PATH))
                .withRequestBody(equalTo(BODY))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing("application/json"))
                .withHeader(HttpHeaders.AUTHORIZATION, absent())
                .withHeader(GatewayKey.HEADER, equalTo(GATEWAY_KEY))
                .withHeader(HEADER, absent()));
    }

    @Test
    void aWrongSecretIs401AndTheCoreIsNotCalled() throws Exception {
        webhook("198.51.100.2", SECRET + "x", BODY).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("webhook.unauthorized"));
        webhook("198.51.100.2", "short", BODY).andExpect(status().isUnauthorized());

        CORE.verify(exactly(0), postRequestedFor(urlEqualTo(CORE_PATH)));
    }

    @Test
    void noSecretIs401AndTheCoreIsNotCalled() throws Exception {
        webhook("198.51.100.3", null, BODY).andExpect(status().isUnauthorized());
        webhook("198.51.100.3", "", BODY).andExpect(status().isUnauthorized());

        CORE.verify(exactly(0), postRequestedFor(urlEqualTo(CORE_PATH)));
    }

    /** The secret is not enough from an address the list does not know. */
    @Test
    void anAddressOutsideTheAllowedListIs403AndTheCoreIsNotCalled() throws Exception {
        webhook("203.0.113.9", SECRET, BODY).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("webhook.forbidden"));

        CORE.verify(exactly(0), postRequestedFor(urlEqualTo(CORE_PATH)));
    }

    /** 60 a minute is the default; here the limit is 5, per address. */
    @Test
    void theLimitIsPerAddressAndWrongSecretsCountToo() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo(CORE_PATH)).willReturn(aResponse().withStatus(202)));

        for (int attempt = 0; attempt < 5; attempt++) {
            webhook("198.51.100.4", SECRET, BODY).andExpect(status().isAccepted());
        }
        webhook("198.51.100.4", SECRET, BODY)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("rate_limit.exceeded"));
        webhook("198.51.100.5", "guess", BODY).andExpect(status().isUnauthorized());

        webhook("198.51.100.6", SECRET, BODY).andExpect(status().isAccepted());
        CORE.verify(exactly(6), postRequestedFor(urlEqualTo(CORE_PATH)));
    }

    @Test
    void theCoresRefusalTravelsOn() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo(CORE_PATH)).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"validation.failed\",\"message\":\"Request has invalid fields\"}")));

        webhook("198.51.100.7", SECRET, "{\"event\":\"item/updated\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
    }

    /** The route is public: no session is needed, and a bogus Bearer is not looked at. */
    @Test
    void aBogusBearerTokenDoesNotMatter() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo(CORE_PATH)).willReturn(aResponse().withStatus(202)));

        mockMvc.perform(request("198.51.100.8", SECRET, BODY).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(status().isAccepted());
    }

    private ResultActions webhook(String address, String secret, String body) throws Exception {
        return mockMvc.perform(request(address, secret, body));
    }

    private MockHttpServletRequestBuilder request(String address, String secret, String body) {
        MockHttpServletRequestBuilder request = post(PATH).with(servletRequest -> {
            servletRequest.setRemoteAddr(address);
            return servletRequest;
        }).contentType(MediaType.APPLICATION_JSON).content(body);
        return secret == null ? request : request.header(HEADER, secret);
    }
}
