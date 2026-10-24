package com.wallet.core.identity;

import com.jayway.jsonpath.JsonPath;
import com.wallet.core.MutableClock;
import com.wallet.core.RecordingMailTransport;
import com.wallet.core.TestClockConfiguration;
import com.wallet.core.TestMailConfiguration;
import com.wallet.core.TestMockMvc;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.identity.controller.ClientAddress;
import com.wallet.core.identity.service.Base32;
import com.wallet.core.identity.service.Totp;
import com.wallet.core.mail.OutgoingEmail;
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

import static com.wallet.core.identity.IdentityClient.HOME_ADDRESS;
import static com.wallet.core.identity.IdentityClient.PASSWORD;
import static com.wallet.core.identity.IdentityClient.refreshTokenOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Changing the password while signed in, and asking for new recovery codes. */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class, TestMailConfiguration.class})
class PasswordChangeIT {

    private static final String NEW_PASSWORD = "a brand new password";

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
    void theRightCurrentPasswordChangesItAndEndsEverySession() throws Exception {
        String current = refreshTokenOf(identity.login(email, PASSWORD).andExpect(status().isOk()));
        String other = refreshTokenOf(identity.login(email, PASSWORD).andExpect(status().isOk()));

        identity.changePassword(accessToken, PASSWORD, NEW_PASSWORD, HOME_ADDRESS).andExpect(status().isNoContent());

        identity.login(email, PASSWORD).andExpect(status().isUnauthorized());
        identity.login(email, NEW_PASSWORD).andExpect(status().isOk());
        // Every login of the account, the one that asked included: the app signs in again.
        identity.refresh(current).andExpect(status().isUnauthorized());
        identity.refresh(other).andExpect(status().isUnauthorized());
        OutgoingEmail notice = mail.awaitNth(email, 2);
        assertThat(notice.subject()).isEqualTo("Sua senha do Wallet foi trocada");
        assertThat(notice.text()).doesNotContain("http");
    }


    /** A JWT cannot be revoked, so the filter refuses the ones issued before the change (finding of the password review). */
    @Test
    void anAccessTokenIssuedBeforeTheChangeIs401AndANewOneWorks() throws Exception {
        clock.advance(Duration.ofSeconds(5));

        identity.changePassword(accessToken, PASSWORD, NEW_PASSWORD, HOME_ADDRESS).andExpect(status().isNoContent());

        identity.me(accessToken).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthenticated"));
        // Signing in again in the very second of the change works: iat has second precision.
        String fresh = JsonPath.read(identity.login(email, NEW_PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        identity.me(fresh).andExpect(status().isOk());
    }
    @Test
    void aWrongCurrentPasswordIs403AndCountsTowardTheLockout() throws Exception {
        identity.changePassword(accessToken, "not the password", NEW_PASSWORD, HOME_ADDRESS)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.wrong_password"));
        for (int attempt = 1; attempt < 5; attempt++) {
            identity.changePassword(accessToken, "not the password", NEW_PASSWORD, HOME_ADDRESS);
        }

        identity.changePassword(accessToken, PASSWORD, NEW_PASSWORD, HOME_ADDRESS)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("auth.locked"));
        identity.login(email, PASSWORD, null, "203.0.113.77").andExpect(status().isOk());
    }

    @Test
    void theSamePasswordAgainOrAShortOneIs400() throws Exception {
        identity.changePassword(accessToken, PASSWORD, PASSWORD, HOME_ADDRESS)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
        identity.changePassword(accessToken, PASSWORD, "short", HOME_ADDRESS)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        identity.login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void newRecoveryCodesReplaceTheOldOnes() throws Exception {
        TurnedOn turnedOn = turnOn();
        clock.advance(Duration.ofSeconds(30));

        List<String> fresh = JsonPath.read(newCodes(PASSWORD, codeNow(turnedOn.secret())).andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10))
                .andReturn().getResponse().getContentAsString(), "$.recoveryCodes");

        assertThat(fresh).doesNotHaveDuplicates().doesNotContainAnyElementsOf(turnedOn.recoveryCodes());
        assertThat(jdbc.queryForObject("select count(*) from recovery_codes c join users u on u.id = c.user_id where u.email = ?",
                Integer.class, email)).isEqualTo(10);
        identity.login(email, PASSWORD, turnedOn.recoveryCodes().getFirst(), HOME_ADDRESS)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_two_factor_code"));
        identity.login(email, PASSWORD, fresh.getFirst(), HOME_ADDRESS).andExpect(status().isOk());
    }

    @Test
    void newRecoveryCodesTakeThePasswordAndACode() throws Exception {
        TurnedOn turnedOn = turnOn();
        clock.advance(Duration.ofSeconds(30));

        newCodes("not the password", codeNow(turnedOn.secret()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.wrong_password"));
        // 403 and not 401: the app would take a 401 for an expired session.
        newCodes(PASSWORD, "000000".equals(codeNow(turnedOn.secret())) ? "111111" : "000000")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.invalid_two_factor_code"));

        // None of the refusals touched the codes the person has.
        assertThat(jdbc.queryForObject("select count(*) from recovery_codes c join users u on u.id = c.user_id "
                + "where u.email = ? and c.used_at is null", Integer.class, email)).isEqualTo(10);
    }

    @Test
    void withTheSecondFactorOffThereAreNoCodesToRenew() throws Exception {
        newCodes(PASSWORD, "123456")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("two_factor.not_enabled"));
    }

    private ResultActions newCodes(String password, String code) throws Exception {
        return identity.json(post("/internal/me/two-factor/recovery-codes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(ClientAddress.HEADER, HOME_ADDRESS),
                "{\"password\":\"%s\",\"code\":\"%s\"}".formatted(password, code));
    }

    private TurnedOn turnOn() throws Exception {
        String secret = JsonPath.read(identity.json(post("/internal/me/two-factor/setup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken), "{\"password\":\"%s\"}".formatted(PASSWORD))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.secret");
        List<String> recoveryCodes = JsonPath.read(identity.json(post("/internal/me/two-factor/enable")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken), "{\"code\":\"%s\"}".formatted(codeNow(secret)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.recoveryCodes");
        return new TurnedOn(secret, recoveryCodes);
    }

    private String codeNow(String secret) {
        return Totp.code(Base32.decode(secret), Totp.step(clock.instant()), Totp.DIGITS);
    }

    private record TurnedOn(String secret, List<String> recoveryCodes) {
    }
}
