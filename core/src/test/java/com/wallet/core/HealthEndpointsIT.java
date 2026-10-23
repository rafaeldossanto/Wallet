package com.wallet.core;

import com.wallet.core.shared.security.GatewayKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The health checks on the real server, both ports: MockMvc has no management port. The core's
 * own port only answers whether the process is alive, from memory; whatever reads the database
 * is on the management port, which listens on loopback only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class HealthEndpointsIT {

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
    private HealthEndpointGroups groups;

    @Autowired
    private Environment environment;

    @Autowired
    private WebApplicationContext context;

    @Test
    void livenessIsOpenOnTheCorePortAndSaysNothingElse() throws Exception {
        HttpResponse<String> response = get(port, "/livez");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).propertyNames()).containsExactly("status");
        assertThat(JSON.readTree(response.body()).get("status").asString()).isEqualTo("UP");
    }

    @Test
    void livenessNeverAsksTheDatabase() {
        assertThat(groups.get("liveness").isMember("db")).isFalse();
        assertThat(groups.get("readiness").isMember("db")).isTrue();
    }

    @Test
    void theFullHealthIsNotOnTheCorePort() throws Exception {
        assertThat(get(port, "/actuator/health").statusCode()).isEqualTo(403);
        assertThat(get(port, "/actuator/health/readiness").statusCode()).isEqualTo(403);

        HttpResponse<String> withKey = http.send(HttpRequest.newBuilder(uri(port, "/actuator/health"))
                .header(GatewayKey.HEADER, TestMockMvc.gatewayKey(context)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(withKey.statusCode()).isNotEqualTo(200);
        assertThat(withKey.body()).doesNotContain("\"db\"");
    }

    @Test
    void theManagementPortTellsWhatIsDown() throws Exception {
        HttpResponse<String> response = get(managementPort, "/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode db = JSON.readTree(response.body()).path("components").path("db");
        assertThat(db.path("status").asString()).isEqualTo("UP");
        assertThat(db.path("details").path("database").asString()).isEqualTo("PostgreSQL");
    }

    @Test
    void readinessOnTheManagementPortChecksTheDatabase() throws Exception {
        HttpResponse<String> response = get(managementPort, "/actuator/health/readiness");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).path("components").propertyNames())
                .containsExactlyInAnyOrder("db", "readinessState");
    }

    @Test
    void theManagementPortListensOnLoopbackOnly() {
        assertThat(environment.getProperty("management.server.address")).isEqualTo("127.0.0.1");
    }

    private HttpResponse<String> get(int target, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(target, path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static URI uri(int target, String path) {
        return URI.create("http://127.0.0.1:" + target + path);
    }
}
