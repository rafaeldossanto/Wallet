package com.wallet.bff;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.wallet.bff.config.GatewayKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The body limit, through MockMvc (always with a Content-Length) and through the real server,
 * where a chunked body comes without one. Nothing too large ever reaches the core.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "wallet.rate-limit.auth-limit=1000")
class RequestBodyLimitTest extends BffTestSupport {

    /** Past the 256 KB default, by less than Tomcat swallows, so the connection stays usable. */
    private static final int TOO_LARGE = 300 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @LocalServerPort
    private int port;

    @Test
    void aDeclaredLengthAboveTheLimitIsRefusedWithItsTraceId() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(TOO_LARGE)))
                .andExpect(status().isContentTooLarge())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.code").value("request.too_large"));

        CORE.verify(0, anyRequestedFor(anyUrl()));
    }

    /** With Expect: 100-continue the client waits for the answer before sending: it never sends the body. */
    @Test
    void theServerRefusesADeclaredLengthWithoutReadingIt() throws Exception {
        HttpResponse<String> response = http.send(register()
                        .expectContinue(true)
                        .POST(HttpRequest.BodyPublishers.ofString(registration(TOO_LARGE)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":\"request.too_large\"");
    }

    @Test
    void aChunkedBodyIsCutAtTheLimit() throws Exception {
        byte[] body = registration(TOO_LARGE).getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> response = http.send(register()
                        .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":\"request.too_large\"");
        CORE.verify(0, postRequestedFor(urlEqualTo("/internal/auth/register")));
    }

    @Test
    void aSmallChunkedBodyGoesThrough() throws Exception {
        CORE.stubFor(WireMock.post(urlEqualTo("/internal/auth/register")).willReturn(aResponse().withStatus(202)));
        byte[] body = "{\"email\":\"a@example.com\",\"password\":\"password123\",\"displayName\":\"A\"}"
                .getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> response = http.send(register()
                        .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(202);
        CORE.verify(postRequestedFor(urlEqualTo("/internal/auth/register"))
                .withHeader(GatewayKey.HEADER, equalTo(GATEWAY_KEY)));
    }

    private HttpRequest.Builder register() {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/register"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json");
    }

    /** A registration whose display name alone makes the body about {@code size} bytes. */
    private static String registration(int size) {
        return "{\"email\":\"big@example.com\",\"password\":\"password123\",\"displayName\":\"" + "x".repeat(size) + "\"}";
    }
}
