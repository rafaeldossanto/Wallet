package com.wallet.bff;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The default: webhooks are off, and the route does not exist, secret or not. */
@SpringBootTest
class WebhookDisabledTest extends BffTestSupport {

    @Test
    void theRouteIs404WhileWebhooksAreOff() throws Exception {
        mockMvc.perform(post("/api/webhooks/pluggy").header("X-Wallet-Webhook-Secret", "a-webhook-secret-of-at-least-32-characters")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":\"e\"}"))
                .andExpect(status().isNotFound());

        CORE.verify(exactly(0), postRequestedFor(urlEqualTo("/internal/webhooks/pluggy")));
    }
}
