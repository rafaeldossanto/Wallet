package com.wallet.core.provider.pluggy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.wallet.core.MutableClock;
import com.wallet.core.provider.config.ProviderWebhookProperties;
import com.wallet.core.provider.service.WebhookRegistration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The command that registers the Wallet's webhooks at Pluggy ({@code POST /webhooks}, the names of
 * the fields and of the events from the official SDK), against a fake Pluggy. It can run any number
 * of times: it lists, then creates what is missing and updates what is there.
 */
class WebhookRegistrationTest {

    private static final String SECRET = "a-webhook-secret-of-at-least-32-characters";
    private static final String URL = "https://wallet.example.com/api/webhooks/pluggy";
    private static final String HEADERS = "{\"X-Wallet-Webhook-Secret\":\"" + SECRET + "\"}";
    private static final String[] EVENTS = {"item/updated", "item/error", "item/deleted",
            "transactions/created", "transactions/updated", "transactions/deleted"};

    @RegisterExtension
    static WireMockExtension pluggy = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @BeforeEach
    void setUp() {
        pluggy.stubFor(post("/auth").willReturn(okJson("{\"apiKey\":\"fake-api-key\"}")));
        pluggy.stubFor(post("/webhooks").willReturn(okJson("{\"id\":\"new\"}")));
        pluggy.stubFor(patch(com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching("/webhooks/.+")).willReturn(okJson("{\"id\":\"x\"}")));
    }

    @Test
    void createsTheSixEventsWhenNothingIsRegistered() {
        listed();

        WebhookRegistration.Report report = registration(SECRET, "https://wallet.example.com", true).register();

        assertThat(report).isEqualTo(new WebhookRegistration.Report(6, 0));
        for (String event : EVENTS) {
            pluggy.verify(postRequestedFor(urlEqualTo("/webhooks"))
                    .withRequestBody(equalToJson("{\"event\":\"%s\",\"url\":\"%s\",\"headers\":%s}".formatted(event, URL, HEADERS))));
        }
        pluggy.verify(6, postRequestedFor(urlEqualTo("/webhooks")));
    }

    @Test
    void updatesWhatIsThereAndCreatesWhatIsMissingAndLeavesOthersAlone() {
        listed(hook("w1", "item/updated", URL), hook("w2", "transactions/created", URL),
                hook("w3", "item/error", "https://someone.else/hook"));

        WebhookRegistration.Report report = registration(SECRET, "https://wallet.example.com", true).register();

        assertThat(report).isEqualTo(new WebhookRegistration.Report(4, 2));
        for (String id : new String[] {"w1", "w2"}) {
            pluggy.verify(patchRequestedFor(urlEqualTo("/webhooks/" + id))
                    .withRequestBody(equalToJson("{\"url\":\"%s\",\"headers\":%s,\"enabled\":true}".formatted(URL, HEADERS))));
        }
        pluggy.verify(0, patchRequestedFor(urlEqualTo("/webhooks/w3")));
        // item/error exists only for someone else's URL: the Wallet's own is created next to it.
        pluggy.verify(postRequestedFor(urlEqualTo("/webhooks"))
                .withRequestBody(equalToJson("{\"event\":\"item/error\",\"url\":\"%s\",\"headers\":%s}".formatted(URL, HEADERS))));
        pluggy.verify(4, postRequestedFor(urlEqualTo("/webhooks")));
    }

    @Test
    void runningItAgainCreatesNothingNew() {
        listed(hook("w1", "item/updated", URL), hook("w2", "item/error", URL), hook("w3", "item/deleted", URL),
                hook("w4", "transactions/created", URL), hook("w5", "transactions/updated", URL),
                hook("w6", "transactions/deleted", URL));

        WebhookRegistration.Report report = registration(SECRET, "https://wallet.example.com", true).register();

        assertThat(report).isEqualTo(new WebhookRegistration.Report(0, 6));
        pluggy.verify(0, postRequestedFor(urlEqualTo("/webhooks")));
    }

    /** A hook Pluggy disabled (after too many failures) is turned on again by the update. */
    @Test
    void aDisabledHookIsEnabledAgain() {
        listed("{\"id\":\"w1\",\"event\":\"item/updated\",\"url\":\"%s\",\"disabledAt\":\"2026-10-01T00:00:00.000Z\"}".formatted(URL));

        registration(SECRET, "https://wallet.example.com", true).register();

        pluggy.verify(patchRequestedFor(urlEqualTo("/webhooks/w1")).withRequestBody(equalToJson(
                "{\"url\":\"%s\",\"headers\":%s,\"enabled\":true}".formatted(URL, HEADERS))));
    }

    @Test
    void withoutASecretOfAtLeast32CharactersNothingIsRegistered() {
        assertThatThrownBy(() -> registration(null, "https://wallet.example.com", true).register())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("WALLET_PLUGGY_WEBHOOK_SECRET");
        assertThatThrownBy(() -> registration("too short", "https://wallet.example.com", true).register())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("WALLET_PLUGGY_WEBHOOK_SECRET");

        assertThat(pluggy.getAllServeEvents()).isEmpty();
    }

    /** Pluggy would post to an address nobody can reach, or in the clear. */
    @Test
    void inProductionTheAddressIsThePublicOneOverHttps() {
        assertThatThrownBy(() -> registration(SECRET, "http://wallet.example.com", true).register())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("https");
        assertThatThrownBy(() -> registration(SECRET, null, true).register())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("WALLET_PUBLIC_URL");
    }

    private void listed(String... webhooks) {
        pluggy.stubFor(get(urlEqualTo("/webhooks?page=1&pageSize=500")).willReturn(okJson(
                "{\"results\":[%s],\"page\":1,\"total\":%d,\"totalPages\":1}".formatted(String.join(",", webhooks), webhooks.length))));
    }

    private static String hook(String id, String event, String url) {
        return "{\"id\":\"%s\",\"event\":\"%s\",\"url\":\"%s\",\"createdAt\":\"2026-10-01T00:00:00.000Z\",\"disabledAt\":null}"
                .formatted(id, event, url);
    }

    private WebhookRegistration registration(String secret, String baseUrl, boolean production) {
        PluggyProperties pluggyProperties = new PluggyProperties(
                pluggy.baseUrl(), "client-id", "client-secret", Duration.ofSeconds(10), Duration.ofHours(2));
        RestClient restClient = PluggyConfig.restClient(RestClient.builder(), pluggyProperties);
        PluggyWebhooks provider = new PluggyWebhooks(
                new PluggyClient(restClient, pluggyProperties, new MutableClock(Instant.parse("2026-10-24T12:00:00Z"))));
        MockEnvironment environment = new MockEnvironment();
        if (production) {
            environment.setActiveProfiles("prod");
        }
        return new WebhookRegistration(provider, new ProviderWebhookProperties(true, secret, baseUrl), environment);
    }
}
