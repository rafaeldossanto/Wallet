package com.wallet.core;

import com.wallet.core.shared.security.GatewayKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static com.wallet.core.TestUsers.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Whoever reaches the core's port without the gateway key gets nothing but the health check. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GatewayKeyIT {

    @Autowired
    private WebApplicationContext context;

    private MockMvc withKey;
    private MockMvc withoutKey;

    @BeforeEach
    void setUp() {
        withKey = TestMockMvc.of(context);
        withoutKey = TestMockMvc.withoutGatewayKey(context);
    }

    @Test
    void loginWithoutTheKeyIsRefusedBeforeAnyPasswordCheck() throws Exception {
        withoutKey.perform(post("/internal/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone@example.com\",\"password\":\"guess\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("gateway.forbidden"));
    }

    @Test
    void registerWithoutTheKeyIsRefused() throws Exception {
        withoutKey.perform(post("/internal/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"password\":\"password123\",\"displayName\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theJwksIsOnlyForTheBff() throws Exception {
        withoutKey.perform(get("/internal/.well-known/jwks.json"))
                .andExpect(status().isForbidden());
        withKey.perform(get("/internal/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"));
    }

    @Test
    void aWrongKeyIsRefused() throws Exception {
        withoutKey.perform(get("/internal/.well-known/jwks.json")
                        .header(GatewayKey.HEADER, TestMockMvc.gatewayKey(context) + "-wrong"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aValidTokenIsNotEnoughWithoutTheKey() throws Exception {
        UUID userId = TestUsers.register(withKey);

        withoutKey.perform(get("/internal/me").with(as(userId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("gateway.forbidden"));
        withKey.perform(get("/internal/me").with(as(userId)))
                .andExpect(status().isOk());
    }

    @Test
    void theHealthCheckStaysOpen() throws Exception {
        withoutKey.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
