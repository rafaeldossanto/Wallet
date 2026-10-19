package com.wallet.core;

import com.wallet.core.shared.security.GatewayKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The body limit on the real server: MockMvc always sets a Content-Length, so only a real Tomcat
 * shows what happens to a chunked body.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RequestBodyLimitIT {

    /** Past the 256 KB default, by less than Tomcat swallows, so the connection stays usable. */
    private static final int TOO_LARGE = 300 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @LocalServerPort
    private int port;

    @Autowired
    private WebApplicationContext context;

    /** With Expect: 100-continue the client waits for the answer before sending: it never sends the body. */
    @Test
    void aDeclaredLengthAboveTheLimitIsRefused() throws Exception {
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
    }

    @Test
    void aSmallChunkedBodyGoesThrough() throws Exception {
        byte[] body = """
                {"email":"chunked+%s@example.com","password":"password123","displayName":"Chunked"}
                """.formatted(System.nanoTime()).getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> response = http.send(register()
                        .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);
    }

    private HttpRequest.Builder register() {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/auth/register"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header(GatewayKey.HEADER, TestMockMvc.gatewayKey(context));
    }

    /** A registration whose display name alone makes the body about {@code size} bytes. */
    private static String registration(int size) {
        return "{\"email\":\"big@example.com\",\"password\":\"password123\",\"displayName\":\"" + "x".repeat(size) + "\"}";
    }
}
