package com.wallet.core.identity;

import com.jayway.jsonpath.JsonPath;
import com.wallet.core.MutableClock;
import com.wallet.core.TestClockConfiguration;
import com.wallet.core.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of the identity module against a real Postgres.
 *
 * <p>MockMvc is built by hand with {@code springSecurity()}: in Spring Boot 4,
 * {@code @AutoConfigureMockMvc} no longer applies the security filter chain.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class AuthFlowIT {

    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MutableClock clock;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        clock.set(TestClockConfiguration.START);
    }

    @Test
    void registersWithLowerCaseEmailAndNeverReturnsThePassword() throws Exception {
        String email = uniqueEmail();

        register(email.toUpperCase(), PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void rejectsSameEmailInAnyCase() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        register(email.toUpperCase(), PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.email_taken"));
    }

    @Test
    void rejectsShortPassword() throws Exception {
        register(uniqueEmail(), "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fields[0].field").value("password"));
    }

    @Test
    void loginGivesTokensThatOpenMe() throws Exception {
        String email = registered();

        String body = login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshTokenExpiresAt").value("2026-10-31T12:00:00Z"))
                .andReturn().getResponse().getContentAsString();

        me(JsonPath.read(body, "$.accessToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void meNeedsAToken() throws Exception {
        mockMvc.perform(get("/internal/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthenticated"));
    }

    @Test
    void accessTokenExpiresAfterFifteenMinutes() throws Exception {
        String accessToken = accessTokenOf(login(registered(), PASSWORD));

        clock.advance(Duration.ofMinutes(16));

        me(accessToken).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmailAndWrongPasswordGetTheSameAnswer() throws Exception {
        login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_credentials"));

        login(registered(), "wrong password!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_credentials"));
    }

    @Test
    void fiveWrongPasswordsLockTheAccountForFifteenMinutes() throws Exception {
        String email = registered();
        for (int attempt = 0; attempt < 5; attempt++) {
            login(email, "wrong password!").andExpect(status().isUnauthorized());
        }

        login(email, PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("auth.locked"));

        clock.advance(Duration.ofMinutes(16));
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void refreshRotatesTheToken() throws Exception {
        String first = refreshTokenOf(login(registered(), PASSWORD));

        String second = refreshTokenOf(refresh(first).andExpect(status().isOk()));
        String third = refreshTokenOf(refresh(second).andExpect(status().isOk()));

        assertThat(second).isNotEqualTo(first);
        assertThat(third).isNotEqualTo(second);
    }

    @Test
    void reusingAnOldTokenRevokesTheWholeSession() throws Exception {
        String first = refreshTokenOf(login(registered(), PASSWORD));
        String second = refreshTokenOf(refresh(first));

        clock.advance(Duration.ofMinutes(2));

        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.refresh_reused"));
        refresh(second)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_refresh_token"));
    }

    @Test
    void deviceThatLostTheResponseCanRetryWithinOneMinute() throws Exception {
        String first = refreshTokenOf(login(registered(), PASSWORD));
        String lostSuccessor = refreshTokenOf(refresh(first));

        clock.advance(Duration.ofSeconds(30));

        String retried = refreshTokenOf(refresh(first).andExpect(status().isOk()));
        refresh(retried).andExpect(status().isOk());
        refresh(lostSuccessor)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_refresh_token"));
    }

    @Test
    void refreshTokenExpiresAfterThirtyDays() throws Exception {
        String token = refreshTokenOf(login(registered(), PASSWORD));

        clock.advance(Duration.ofDays(31));

        refresh(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_refresh_token"));
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        String token = refreshTokenOf(login(registered(), PASSWORD));

        mockMvc.perform(post("/internal/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(token)))
                .andExpect(status().isNoContent());

        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void healthStaysPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void publishesOnlyThePublicHalfOfTheSigningKey() throws Exception {
        mockMvc.perform(get("/internal/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").value("wallet-core-1"))
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist());
    }

    private String registered() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());
        return email;
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/internal/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"Rafael\"}".formatted(email, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/internal/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/internal/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private ResultActions me(String accessToken) throws Exception {
        return mockMvc.perform(get("/internal/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private static String accessTokenOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private static String refreshTokenOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.refreshToken");
    }

    private static String uniqueEmail() {
        return "rafael+" + UUID.randomUUID() + "@example.com";
    }
}
