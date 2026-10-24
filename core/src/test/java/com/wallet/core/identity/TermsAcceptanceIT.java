package com.wallet.core.identity;

import com.wallet.core.MutableClock;
import com.wallet.core.TestClockConfiguration;
import com.wallet.core.TestMockMvc;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.TestMailConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.wallet.core.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The acceptance of the Terms of Use and the Privacy Policy, with the version of the text (L15): asked
 * at sign-up, and asked again of whoever has an older version, or none.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class, TestMailConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class TermsAcceptanceIT {

    private static final String CURRENT = "2026-10-preliminar";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clock.set(TestClockConfiguration.START);
        mockMvc = TestMockMvc.of(context);
    }

    @Test
    void aSignUpWithoutTheAcceptanceIsRefusedAndCreatesNothing() throws Exception {
        String email = "terms+" + UUID.randomUUID() + "@example.com";

        signUp(email, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fields[0].field").value("acceptTerms"));
        signUp(email, ",\"acceptTerms\":false").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
        signUp(email, ",\"acceptTerms\":null").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email)).isZero();
    }

    @Test
    void aSignUpWithTheAcceptanceRecordsTheVersionAndTheMoment(CapturedOutput output) throws Exception {
        String email = "terms+" + UUID.randomUUID() + "@example.com";

        signUp(email, ",\"acceptTerms\":true").andExpect(status().isAccepted());

        Map<String, Object> user = jdbc.queryForMap("select id, terms_version, terms_accepted_at from users where email = ?", email);
        assertThat(user.get("terms_version")).isEqualTo(CURRENT);
        assertThat(((java.sql.Timestamp) user.get("terms_accepted_at")).toInstant()).isEqualTo(TestClockConfiguration.START);
        // The id and the version, nothing of the person.
        assertThat(output.getAll()).contains("[LEGAL] User " + user.get("id") + " accepted the terms at sign-up, version " + CURRENT)
                .doesNotContain(email);
    }

    /** The latest sign-up of an unconfirmed e-mail wins, and so does its acceptance. */
    @Test
    void aLaterSignUpOfAnUnconfirmedAccountRecordsItsOwnAcceptance() throws Exception {
        String email = "terms+" + UUID.randomUUID() + "@example.com";
        signUp(email, ",\"acceptTerms\":true").andExpect(status().isAccepted());
        jdbc.update("update users set terms_version = '2020-old', terms_accepted_at = null where email = ?", email);
        clock.advance(Duration.ofMinutes(5));

        signUp(email, ",\"acceptTerms\":true").andExpect(status().isAccepted());

        Map<String, Object> user = jdbc.queryForMap("select terms_version, terms_accepted_at from users where email = ?", email);
        assertThat(user.get("terms_version")).isEqualTo(CURRENT);
        assertThat(((java.sql.Timestamp) user.get("terms_accepted_at")).toInstant())
                .isEqualTo(TestClockConfiguration.START.plus(Duration.ofMinutes(5)));
    }

    @Test
    void someoneWithoutAVersionOrWithAnOlderOneMustAcceptAgain() throws Exception {
        UUID userId = TestUsers.register(mockMvc);
        me(userId).andExpect(jsonPath("$.termsAcceptanceRequired").value(false))
                .andExpect(jsonPath("$.termsVersion").value(CURRENT));

        jdbc.update("update users set terms_version = null, terms_accepted_at = null where id = ?", userId);
        me(userId).andExpect(jsonPath("$.termsAcceptanceRequired").value(true))
                .andExpect(jsonPath("$.termsVersion").value(CURRENT))
                .andExpect(jsonPath("$.termsUrl").value("http://localhost:5181/termos.html"))
                .andExpect(jsonPath("$.privacyUrl").value("http://localhost:5181/privacidade.html"));

        jdbc.update("update users set terms_version = '2020-old' where id = ?", userId);
        me(userId).andExpect(jsonPath("$.termsAcceptanceRequired").value(true));
    }

    @Test
    void acceptingTheCurrentVersionRecordsItAndStopsTheQuestion(CapturedOutput output) throws Exception {
        UUID userId = TestUsers.register(mockMvc);
        jdbc.update("update users set terms_version = null, terms_accepted_at = null where id = ?", userId);

        accept(userId, CURRENT).andExpect(status().isNoContent());

        me(userId).andExpect(jsonPath("$.termsAcceptanceRequired").value(false));
        Map<String, Object> user = jdbc.queryForMap("select terms_version, terms_accepted_at from users where id = ?", userId);
        assertThat(user.get("terms_version")).isEqualTo(CURRENT);
        assertThat(((java.sql.Timestamp) user.get("terms_accepted_at")).toInstant()).isEqualTo(TestClockConfiguration.START);
        assertThat(output.getAll()).contains("[LEGAL] User " + userId + " accepted the terms, version " + CURRENT);
    }

    /** Asking twice is harmless and keeps the moment of the first acceptance. */
    @Test
    void acceptingTwiceKeepsTheFirstMoment() throws Exception {
        UUID userId = TestUsers.register(mockMvc);
        jdbc.update("update users set terms_version = null, terms_accepted_at = null where id = ?", userId);
        accept(userId, CURRENT).andExpect(status().isNoContent());
        clock.advance(Duration.ofHours(2));

        accept(userId, CURRENT).andExpect(status().isNoContent());

        Map<String, Object> user = jdbc.queryForMap("select terms_accepted_at from users where id = ?", userId);
        assertThat(((java.sql.Timestamp) user.get("terms_accepted_at")).toInstant()).isEqualTo(TestClockConfiguration.START);
    }

    /** What the user read is not the current text any more: nothing is recorded, and the app asks again. */
    @Test
    void aWrongVersionIsRefusedAndRecordsNothing() throws Exception {
        UUID userId = TestUsers.register(mockMvc);
        jdbc.update("update users set terms_version = null, terms_accepted_at = null where id = ?", userId);

        accept(userId, "2020-old").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("legal.terms_outdated"));

        assertThat(jdbc.queryForObject("select terms_version from users where id = ?", String.class, userId)).isNull();
        me(userId).andExpect(jsonPath("$.termsAcceptanceRequired").value(true));
    }

    @Test
    void theRouteNeedsAVersionAndAnAuthenticatedUser() throws Exception {
        UUID userId = TestUsers.register(mockMvc);

        mockMvc.perform(post("/internal/me/terms").with(as(userId)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation.failed"));
        mockMvc.perform(post("/internal/me/terms").contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"" + CURRENT + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions signUp(String email, String acceptance) throws Exception {
        return mockMvc.perform(post("/internal/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"Test\"%s}".formatted(email, TestUsers.PASSWORD, acceptance)));
    }

    private ResultActions accept(UUID userId, String version) throws Exception {
        return mockMvc.perform(post("/internal/me/terms").with(as(userId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"%s\"}".formatted(version)));
    }

    private ResultActions me(UUID userId) throws Exception {
        return mockMvc.perform(get("/internal/me").with(as(userId))).andExpect(status().isOk());
    }
}
