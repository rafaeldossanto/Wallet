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
import com.wallet.core.mail.OutgoingEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;

import static com.wallet.core.RecordingMailTransport.tokenIn;
import static com.wallet.core.identity.IdentityClient.HOME_ADDRESS;
import static com.wallet.core.identity.IdentityClient.PASSWORD;
import static com.wallet.core.identity.IdentityClient.refreshTokenOf;
import static com.wallet.core.identity.IdentityClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Esqueci minha senha": the link goes only to a confirmed account, and opening it ends every
 * session of the account, clears its lockout and keeps the second factor on. This closes what
 * sign-up alone could not (finding A16): someone who signs up with the victim's e-mail and waits
 * for the victim to confirm it is undone by the victim resetting the password.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class, TestMailConfiguration.class})
class PasswordResetFlowIT {

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

    @BeforeEach
    void setUp() {
        identity = new IdentityClient(TestMockMvc.of(context), mail);
        clock.set(TestClockConfiguration.START);
    }

    @Test
    void aConfirmedAccountGetsTheLinkAndTheAnswerIsAlways202() throws Exception {
        String email = identity.confirmedAccount();

        identity.forgotPassword(email).andExpect(status().isAccepted()).andExpect(content().string(""));

        OutgoingEmail link = mail.awaitNth(email, 2);
        assertThat(link.subject()).isEqualTo("Crie uma senha nova no Wallet");
        assertThat(link.kind()).isEqualTo("password-reset");
        assertThat(link.text()).contains("http://localhost:8080/password/reset#token=").contains("1 hora");
        assertThat(link.hasUnsubscribeUrl()).isFalse();
    }

    /** Nothing in the answer, or in the inbox of a stranger, tells whether an e-mail has an account. */
    @Test
    void anUnknownOrUnconfirmedEmailGetsNothing() throws Exception {
        String unknown = uniqueEmail();
        String pending = uniqueEmail();
        identity.register(pending, PASSWORD).andExpect(status().isAccepted());
        mail.await(pending);

        identity.forgotPassword(unknown).andExpect(status().isAccepted());
        identity.forgotPassword(pending).andExpect(status().isAccepted());

        mail.assertNothingMoreTo(unknown, 0);
        mail.assertNothingMoreTo(pending, 1);
    }

    @Test
    void threeLinksAnHourPerAccount() throws Exception {
        String email = identity.confirmedAccount();

        for (int request = 0; request < 4; request++) {
            identity.forgotPassword(email).andExpect(status().isAccepted());
        }
        mail.awaitNth(email, 4);
        mail.assertNothingMoreTo(email, 4);

        clock.advance(Duration.ofMinutes(61));
        identity.forgotPassword(email).andExpect(status().isAccepted());
        mail.awaitNth(email, 5);
    }

    @Test
    void theLinkChangesThePasswordAndEndsEverySession() throws Exception {
        String email = identity.confirmedAccount();
        String phone = refreshTokenOf(identity.login(email, PASSWORD).andExpect(status().isOk()));
        String laptop = refreshTokenOf(identity.login(email, PASSWORD).andExpect(status().isOk()));
        identity.forgotPassword(email);
        String token = tokenIn(mail.awaitNth(email, 2));

        identity.resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());

        identity.login(email, PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_credentials"));
        identity.login(email, NEW_PASSWORD).andExpect(status().isOk());
        identity.refresh(phone).andExpect(status().isUnauthorized());
        identity.refresh(laptop).andExpect(status().isUnauthorized());
        OutgoingEmail notice = mail.awaitNth(email, 3);
        assertThat(notice.subject()).isEqualTo("Sua senha do Wallet foi trocada");
        assertThat(notice.kind()).isEqualTo("password-changed");
        assertThat(notice.text()).doesNotContain("http").contains("Esqueci minha senha");
    }


    @Test
    void anAccessTokenIssuedBeforeTheResetIs401() throws Exception {
        String email = identity.confirmedAccount();
        String stolen = identity.accessTokenOf(email);
        identity.forgotPassword(email);
        String token = tokenIn(mail.awaitNth(email, 2));
        clock.advance(Duration.ofSeconds(5));

        identity.resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());

        identity.me(stolen).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthenticated"));
        identity.me(identity.accessTokenOf(email, NEW_PASSWORD)).andExpect(status().isOk());
    }
    /** The owner locked out by guesses can get back in with the new password. */
    @Test
    void theResetClearsTheLockout() throws Exception {
        String email = identity.confirmedAccount();
        for (int attempt = 0; attempt < 5; attempt++) {
            identity.login(email, "wrong password!", null, HOME_ADDRESS);
        }
        identity.login(email, PASSWORD).andExpect(status().isLocked());
        // And 20 failures from as many addresses locked the account for everyone.
        for (int attempt = 0; attempt < 20; attempt++) {
            identity.login(email, "wrong password!", null, "198.51.100." + (100 + attempt));
        }
        identity.login(email, PASSWORD, null, "203.0.113.99").andExpect(status().isLocked());
        identity.forgotPassword(email);
        String token = tokenIn(resetLinkSentTo(email));

        identity.resetPassword(token, NEW_PASSWORD, HOME_ADDRESS).andExpect(status().isNoContent());

        identity.login(email, NEW_PASSWORD).andExpect(status().isOk());
        identity.login(email, NEW_PASSWORD, null, "203.0.113.99").andExpect(status().isOk());
    }

    @Test
    void aLinkWorksOnceAndOnlyTheNewestWorks() throws Exception {
        String email = identity.confirmedAccount();
        identity.forgotPassword(email);
        String first = tokenIn(mail.awaitNth(email, 2));
        identity.forgotPassword(email);
        String second = tokenIn(mail.awaitNth(email, 3));

        identity.resetPassword(first, NEW_PASSWORD).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("auth.link_invalid"));
        identity.resetPassword(second, NEW_PASSWORD).andExpect(status().isNoContent());
        identity.resetPassword(second, "yet another password").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("auth.link_invalid"));

        identity.login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void anExpiredOrUnknownLinkIsRefused() throws Exception {
        String email = identity.confirmedAccount();
        identity.forgotPassword(email);
        String token = tokenIn(mail.awaitNth(email, 2));

        clock.advance(Duration.ofMinutes(61));

        identity.resetPassword(token, NEW_PASSWORD).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("auth.link_invalid"));
        identity.resetPassword("never-issued", NEW_PASSWORD).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("auth.link_invalid"));
        identity.login(email, PASSWORD).andExpect(status().isOk());
    }

    /** One kind of link cannot stand in for another: the sign-up link does not reset a password. */
    @Test
    void theLinksOfTheOtherKindsDoNotReset() throws Exception {
        String email = uniqueEmail();
        identity.register(email, PASSWORD).andExpect(status().isAccepted());
        String signUpLink = tokenIn(mail.await(email));

        identity.resetPassword(signUpLink, NEW_PASSWORD).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("auth.link_invalid"));

        // The sign-up link was not spent by that attempt.
        identity.confirm(signUpLink).andExpect(status().isOk());

        // And the reset link does not confirm or move an account.
        identity.forgotPassword(email);
        String resetLink = tokenIn(mail.awaitNth(email, 2));
        identity.confirm(resetLink).andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("auth.link_invalid"));
        identity.resetPassword(resetLink, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void aShortNewPasswordIsRefusedAndTheLinkSurvives() throws Exception {
        String email = identity.confirmedAccount();
        identity.forgotPassword(email);
        String token = tokenIn(mail.awaitNth(email, 2));

        identity.resetPassword(token, "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        identity.resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    /** Whoever has the inbox does not get past the authenticator: the second factor stays on. */
    @Test
    void theSecondFactorStaysOn() throws Exception {
        String email = identity.confirmedAccount();
        String accessToken = identity.accessTokenOf(email);
        String secret = JsonPath.read(identity.json(post("/internal/me/two-factor/setup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken), "{\"password\":\"%s\"}".formatted(PASSWORD))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.secret");
        identity.json(post("/internal/me/two-factor/enable").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken),
                "{\"code\":\"%s\"}".formatted(Totp.code(Base32.decode(secret), Totp.step(clock.instant()), Totp.DIGITS)))
                .andExpect(status().isOk());
        identity.forgotPassword(email);
        String token = tokenIn(mail.awaitNth(email, 2));

        identity.resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());

        identity.login(email, NEW_PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.two_factor_required"));
        assertThat(jdbc.queryForObject("select totp_enabled_at is not null from users where email = ?", Boolean.class, email))
                .isTrue();
    }

    /** The reset e-mail among the others (the lockout notices go to the same inbox). */
    private OutgoingEmail resetLinkSentTo(String email) throws InterruptedException {
        for (int attempt = 0; attempt < 250; attempt++) {
            for (OutgoingEmail sent : mail.to(email)) {
                if ("password-reset".equals(sent.kind())) {
                    return sent;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("No password reset e-mail to " + email + ": " + mail.to(email));
    }
}
