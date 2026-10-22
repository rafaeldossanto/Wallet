package com.wallet.bff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The health checks on the real server, both ports (finding A18): MockMvc has no management port.
 * The public port only says whether the process is alive; the details are on the management port,
 * which listens on loopback only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointsTest extends BffTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private Environment environment;

    @Test
    void livenessIsOpenOnThePublicPortAndSaysNothingElse() throws Exception {
        HttpResponse<String> response = get(port, "/livez");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).propertyNames()).containsExactly("status");
    }

    @Test
    void theDetailsAreNotOnThePublicPort() throws Exception {
        HttpResponse<String> response = get(port, "/actuator/health");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).doesNotContain("components");
    }

    @Test
    void theManagementPortTellsWhatIsDown() throws Exception {
        HttpResponse<String> health = get(managementPort, "/actuator/health");
        HttpResponse<String> readiness = get(managementPort, "/actuator/health/readiness");

        assertThat(health.statusCode()).isEqualTo(200);
        JsonNode components = JSON.readTree(health.body()).path("components");
        assertThat(components.path("diskSpace").path("status").asString()).isEqualTo("UP");
        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(readiness.body()).path("components").propertyNames()).containsExactly("readinessState");
    }

    @Test
    void theManagementPortListensOnLoopbackOnly() {
        assertThat(environment.getProperty("management.server.address")).isEqualTo("127.0.0.1");
    }

    private HttpResponse<String> get(int target, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + target + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
