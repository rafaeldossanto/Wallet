package com.wallet.core.identity;

import com.jayway.jsonpath.JsonPath;
import com.wallet.core.MutableClock;
import com.wallet.core.RecordingMailTransport;
import com.wallet.core.TestClockConfiguration;
import com.wallet.core.TestMailConfiguration;
import com.wallet.core.TestMockMvc;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.identity.service.Base32;
import com.wallet.core.identity.service.Totp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.util.List;

import static com.wallet.core.identity.IdentityClient.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The second factor end to end: set-up, sign-in with a code or a recovery code, turning it off. */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class, TestMailConfiguration.class})
class TwoFactorIT {

    private static final String ADDRESS = "203.0.113.50";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MutableClock clock;

    @Autowired
    private RecordingMailTransport mail;

    @Autowired
    private JdbcTemplate jdbc;

    private IdentityClient identity;
    private String email;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        identity = new IdentityClient(TestMockMvc.of(context), mail);
        clock.set(TestClockConfiguration.START);
        email = identity.confirmedAccount();
        accessToken = identity.accessTokenOf(email);
    }

    @Test
    void turningItOnTakesThePasswordAndTheFirstCode() throws Exception {
        startSetUp("not the password")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.wrong_password"));

        String body = startSetUp(PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(body, "$.secret");
        assertThat(JsonPath.<String>read(body, "$.otpauthUri"))
                .startsWith("otpauth://totp/Wallet:" + email.replace("+", "%2B").replace("@", "%40"))
                .contains("secret=" + secret, "issuer=Wallet", "digits=6", "period=30");

        // A set-up nobody confirmed asks for nothing at sign-in yet.
        identity.login(email, PASSWORD).andExpect(status().isOk());
        enable("000000")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("two_factor.invalid_code"));
        String recoveryCodes = enable(codeNow(secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(recoveryCodes, "$.recoveryCodes"))
                .allMatch(code -> code.matches("[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}"))
                .doesNotHaveDuplicates();
        identity.me(accessToken).andExpect(jsonPath("$.twoFactorEnabled").value(true));
    }

    @Test
    void theSecretIsStoredEncrypted() throws Exception {
        String secret = turnOn().secret();

        String stored = jdbc.queryForObject("select totp_secret from users where email = ?", String.class, email);
        assertThat(stored).startsWith("v1:").doesNotContain(secret);
    }

    @Test
    void signingInThenAsksForTheCode() throws Exception {
        String secret = turnOn().secret();
        clock.advance(Duration.ofSeconds(30));

        identity.login(email, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.two_factor_required"));
        identity.login(email, PASSWORD, wrongCode(secret), ADDRESS)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_two_factor_code"));
        identity.login(email, PASSWORD, codeNow(secret), ADDRESS).andExpect(status().isOk());
    }

    /** A code inside its 30-second window would otherwise work for whoever saw it typed. */
    @Test
    void aCodeWorksOnce() throws Exception {
        String secret = turnOn().secret();
        clock.advance(Duration.ofSeconds(30));
        String code = codeNow(secret);

        identity.login(email, PASSWORD, code, ADDRESS).andExpect(status().isOk());
        identity.login(email, PASSWORD, code, ADDRESS)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_two_factor_code"));
    }

    @Test
    void aCodeFromThePreviousStepStillWorks() throws Exception {
        String secret = turnOn().secret();
        clock.advance(Duration.ofSeconds(60));
        String previous = Totp.code(Base32.decode(secret), Totp.step(clock.instant()) - 1, Totp.DIGITS);

        identity.login(email, PASSWORD, previous, ADDRESS).andExpect(status().isOk());
    }

    @Test
    void aRecoveryCodeStandsInForTheAppOnce() throws Exception {
        String recoveryCode = turnOn().recoveryCodes().getFirst();
        String typedCasually = recoveryCode.toLowerCase().replace("-", " ");

        identity.login(email, PASSWORD, typedCasually, ADDRESS).andExpect(status().isOk());
        identity.login(email, PASSWORD, recoveryCode, ADDRESS)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_two_factor_code"));
    }

    /** Six digits are a million guesses: each wrong one counts toward the lockout. */
    @Test
    void wrongCodesCountTowardTheLockout() throws Exception {
        String secret = turnOn().secret();
        for (int attempt = 1; attempt < 5; attempt++) {
            identity.login(email, PASSWORD, wrongCode(secret), ADDRESS).andExpect(status().isUnauthorized());
        }

        identity.login(email, PASSWORD, wrongCode(secret), ADDRESS)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("auth.locked"));
    }

    @Test
    void turningItOffTakesThePasswordAndACode() throws Exception {
        TurnedOn turnedOn = turnOn();
        clock.advance(Duration.ofSeconds(30));

        disable("not the password", codeNow(turnedOn.secret()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.wrong_password"));
        // 403 and not 401: the app would take a 401 for an expired session.
        disable(PASSWORD, wrongCode(turnedOn.secret()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.invalid_two_factor_code"));
        disable(PASSWORD, turnedOn.recoveryCodes().get(3)).andExpect(status().isNoContent());

        identity.login(email, PASSWORD).andExpect(status().isOk());
        identity.me(accessToken).andExpect(jsonPath("$.twoFactorEnabled").value(false));
        assertThat(jdbc.queryForObject("select count(*) from recovery_codes c join users u on u.id = c.user_id where u.email = ?",
                Integer.class, email)).isZero();
    }

    @Test
    void itCannotBeTurnedOnTwiceOrOnWithoutASetUp() throws Exception {
        enable("123456")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("two_factor.not_set_up"));
        turnOn();

        startSetUp(PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("two_factor.already_enabled"));
    }

    private TurnedOn turnOn() throws Exception {
        String secret = JsonPath.read(startSetUp(PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.secret");
        List<String> recoveryCodes = JsonPath.read(enable(codeNow(secret)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.recoveryCodes");
        return new TurnedOn(secret, recoveryCodes);
    }

    private ResultActions startSetUp(String password) throws Exception {
        return identity.json(post("/internal/me/two-factor/setup").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken),
                "{\"password\":\"%s\"}".formatted(password));
    }

    private ResultActions enable(String code) throws Exception {
        return identity.json(post("/internal/me/two-factor/enable").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken),
                "{\"code\":\"%s\"}".formatted(code));
    }

    private ResultActions disable(String password, String code) throws Exception {
        return identity.json(delete("/internal/me/two-factor").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken),
                "{\"password\":\"%s\",\"code\":\"%s\"}".formatted(password, code));
    }

    private String codeNow(String secret) {
        return Totp.code(Base32.decode(secret), Totp.step(clock.instant()), Totp.DIGITS);
    }

    /** Not the code of this step (a neighbour's matching by chance is one in hundreds of thousands). */
    private String wrongCode(String secret) {
        String right = codeNow(secret);
        return right.equals("000000") ? "111111" : "000000";
    }

    private record TurnedOn(String secret, List<String> recoveryCodes) {
    }
}
