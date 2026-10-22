package com.wallet.core.identity;

import com.wallet.core.MutableClock;
import com.wallet.core.RecordingMailTransport;
import com.wallet.core.TestClockConfiguration;
import com.wallet.core.TestMailConfiguration;
import com.wallet.core.TestMockMvc;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.mail.OutgoingEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.wallet.core.RecordingMailTransport.tokenIn;
import static com.wallet.core.identity.IdentityClient.HOME_ADDRESS;
import static com.wallet.core.identity.IdentityClient.PASSWORD;
import static com.wallet.core.identity.IdentityClient.accessTokenOf;
import static com.wallet.core.identity.IdentityClient.refreshTokenOf;
import static com.wallet.core.identity.IdentityClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of the identity module against a real Postgres: sign-up with e-mail confirmation,
 * sign-in with the progressive lockout, tokens.
 *
 * <p>MockMvc comes from {@link TestMockMvc}: security chain applied, gateway key sent. E-mails go
 * to a {@link RecordingMailTransport}, where the tests read the links as their owner would.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class, TestMailConfiguration.class})
class AuthFlowIT {

    private static final String WRONG = "wrong password!";
    private static final Instant LOCKED_FIFTEEN_MINUTES = TestClockConfiguration.START.plus(Duration.ofMinutes(15));

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MutableClock clock;

    @Autowired
    private RecordingMailTransport mail;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    private MockMvc mockMvc;
    private IdentityClient identity;

    @BeforeEach
    void setUp() {
        mockMvc = TestMockMvc.of(context);
        identity = new IdentityClient(mockMvc, mail);
        clock.set(TestClockConfiguration.START);
    }

    // --- Sign-up (A16) ---

    @Test
    void signUpAnswersTheSameWhetherOrNotTheEmailHasAnAccount() throws Exception {
        String taken = identity.confirmedAccount();
        String fresh = uniqueEmail();

        identity.register(fresh, PASSWORD).andExpect(status().isAccepted()).andExpect(content().string(""));
        identity.register(taken, "another password").andExpect(status().isAccepted()).andExpect(content().string(""));

        assertThat(mail.await(fresh).subject()).isEqualTo("Confirme seu e-mail no Wallet");
        OutgoingEmail notice = mail.awaitNth(taken, 2);
        assertThat(notice.subject()).isEqualTo("Tentativa de cadastro no Wallet");
        assertThat(notice.text()).doesNotContain("#token=");
        // The stranger's password went nowhere: the owner's still works, theirs does not.
        identity.login(taken, "another password").andExpect(status().isUnauthorized());
        identity.login(taken, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void aTakenEmailIsToldOnlyOncePerHour() throws Exception {
        String taken = identity.confirmedAccount();

        identity.register(taken, PASSWORD).andExpect(status().isAccepted());
        identity.register(taken, PASSWORD).andExpect(status().isAccepted());
        mail.awaitNth(taken, 2);
        mail.assertNothingMoreTo(taken, 2);

        clock.advance(Duration.ofMinutes(61));
        identity.register(taken, PASSWORD).andExpect(status().isAccepted());
        assertThat(mail.awaitNth(taken, 3).subject()).isEqualTo("Tentativa de cadastro no Wallet");
    }

    @Test
    void noSignInBeforeTheLinkIsOpenedAndThenItWorks() throws Exception {
        String email = uniqueEmail();
        identity.register(email, PASSWORD).andExpect(status().isAccepted());

        // The right password on an unconfirmed account fails like a wrong one, and the link goes again.
        identity.login(email, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_credentials"));
        String latest = tokenIn(mail.awaitNth(email, 2));

        identity.confirm(latest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ACCOUNT_CONFIRMED"));
        identity.login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void registersTheEmailInLowerCase() throws Exception {
        String email = uniqueEmail();
        identity.register(email.toUpperCase(), PASSWORD).andExpect(status().isAccepted());
        identity.confirm(tokenIn(mail.await(email))).andExpect(status().isOk());

        identity.me(identity.accessTokenOf(email.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.twoFactorEnabled").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void aLinkOpensOnceAndOnlyTheNewestWorks() throws Exception {
        String email = uniqueEmail();
        identity.register(email, PASSWORD).andExpect(status().isAccepted());
        String first = tokenIn(mail.await(email));
        identity.resend(email).andExpect(status().isAccepted());
        String second = tokenIn(mail.awaitNth(email, 2));

        identity.confirm(first)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("auth.link_invalid"));
        identity.confirm(second).andExpect(status().isOk());
        identity.confirm(second)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("auth.link_invalid"));
    }

    @Test
    void aLinkExpiresAfterADay() throws Exception {
        String email = uniqueEmail();
        identity.register(email, PASSWORD).andExpect(status().isAccepted());
        String token = tokenIn(mail.await(email));

        clock.advance(Duration.ofHours(25));

        identity.confirm(token).andExpect(status().isUnprocessableContent());
        identity.login(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownLinkIsRefused() throws Exception {
        identity.confirm("not-a-token").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("auth.link_invalid"));
    }

    @Test
    void linksGoAtMostThreeTimesAnHour() throws Exception {
        String email = uniqueEmail();
        identity.register(email, PASSWORD).andExpect(status().isAccepted());
        for (int attempt = 0; attempt < 4; attempt++) {
            identity.resend(email).andExpect(status().isAccepted());
        }
        mail.awaitNth(email, 3);
        mail.assertNothingMoreTo(email, 3);

        clock.advance(Duration.ofMinutes(61));
        identity.resend(email).andExpect(status().isAccepted());
        mail.awaitNth(email, 4);
    }

    @Test
    void resendingForAnEmailWithoutAnAccountAnswersTheSame() throws Exception {
        String email = uniqueEmail();

        identity.resend(email).andExpect(status().isAccepted()).andExpect(content().string(""));

        mail.assertNothingMoreTo(email, 0);
    }

    /**
     * Keeping the first sign-up would let a stranger sign up first with someone else's e-mail and
     * wait for its owner to confirm an account holding the stranger's password.
     */
    @Test
    void theLatestSignUpOfAnUnconfirmedEmailWinsWithItsPassword() throws Exception {
        String email = uniqueEmail();
        identity.register(email, "stranger's password").andExpect(status().isAccepted());
        identity.register(email, PASSWORD).andExpect(status().isAccepted());

        identity.confirm(tokenIn(mail.awaitNth(email, 2))).andExpect(status().isOk());

        identity.login(email, "stranger's password").andExpect(status().isUnauthorized());
        identity.login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void rejectsShortPassword() throws Exception {
        identity.register(uniqueEmail(), "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fields[0].field").value("password"));
    }

    // --- Tokens (A14) ---

    @Test
    void loginGivesTokensForTheWalletApiThatOpenMe() throws Exception {
        String email = identity.confirmedAccount();

        String accessToken = accessTokenOf(identity.login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshTokenExpiresAt").value("2026-10-31T12:00:00Z")));

        assertThat(jwtDecoder.decode(accessToken).getAudience()).containsExactly("wallet-api");
        identity.me(accessToken).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
    }

    /** Signed with the core's own key, but issued before the audience existed: the app renews it. */
    @Test
    void aTokenWithoutTheAudienceIsRefused() throws Exception {
        String email = identity.confirmedAccount();
        String userId = jwtDecoder.decode(identity.accessTokenOf(email)).getSubject();
        Instant now = clock.instant();
        String withoutAudience = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder().issuer("wallet-core").subject(userId).issuedAt(now)
                        .expiresAt(now.plus(Duration.ofMinutes(15))).build())).getTokenValue();

        identity.me(withoutAudience)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthenticated"));
    }

    @Test
    void meNeedsAToken() throws Exception {
        mockMvc.perform(get("/internal/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthenticated"));
    }

    @Test
    void accessTokenExpiresAfterFifteenMinutes() throws Exception {
        String accessToken = identity.accessTokenOf(identity.confirmedAccount());

        clock.advance(Duration.ofMinutes(16));

        identity.me(accessToken).andExpect(status().isUnauthorized());
    }

    // --- Sign-in and lockout (A4, A5) ---

    @Test
    void unknownEmailAndWrongPasswordGetTheSameAnswer() throws Exception {
        identity.login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_credentials"));

        identity.login(identity.confirmedAccount(), WRONG)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_credentials"));
    }

    /** Only the e-mail of a victim no longer keeps the victim out: the guesser's address is what locks. */
    @Test
    void fiveWrongPasswordsLockOnlyTheAddressTheyCameFrom() throws Exception {
        String email = identity.confirmedAccount();
        String guesser = "203.0.113.7";
        for (int attempt = 1; attempt < 5; attempt++) {
            identity.login(email, WRONG, null, guesser).andExpect(status().isUnauthorized());
        }

        // The fifth one says so itself, with until when as a field: the app says the time.
        identity.login(email, WRONG, null, guesser)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("auth.locked"))
                .andExpect(jsonPath("$.lockedUntil").value(LOCKED_FIFTEEN_MINUTES.toString()));
        identity.login(email, PASSWORD, null, guesser).andExpect(status().isLocked());
        identity.login(email, PASSWORD, null, HOME_ADDRESS).andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(16));
        identity.login(email, PASSWORD, null, guesser).andExpect(status().isOk());
    }

    @Test
    void eachLockoutLastsTwiceThePreviousOne() throws Exception {
        String email = identity.confirmedAccount();
        String guesser = "203.0.113.8";
        guessFiveTimes(email, guesser).andExpect(jsonPath("$.lockedUntil").value(LOCKED_FIFTEEN_MINUTES.toString()));

        clock.advance(Duration.ofMinutes(16));
        Instant secondUntil = clock.instant().plus(Duration.ofMinutes(30));
        guessFiveTimes(email, guesser).andExpect(jsonPath("$.lockedUntil").value(secondUntil.toString()));
    }

    @Test
    void guessesFromManyAddressesLockTheAccountForAllAndTellTheOwner() throws Exception {
        String email = identity.confirmedAccount();
        for (int address = 1; address <= 5; address++) {
            for (int attempt = 0; attempt < 4; attempt++) {
                identity.login(email, WRONG, null, "203.0.113." + (100 + address));
            }
        }

        identity.login(email, PASSWORD, null, HOME_ADDRESS)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.lockedUntil").value(LOCKED_FIFTEEN_MINUTES.toString()));
        OutgoingEmail notice = mail.awaitNth(email, 2);
        assertThat(notice.subject()).isEqualTo("Sua conta do Wallet foi bloqueada por um tempo");
        assertThat(notice.text()).contains("de qualquer lugar").contains("01/10/2026 às 09:15");
    }

    /** A locked e-mail looks the same with or without an account behind it. */
    @Test
    void anEmailWithoutAnAccountLocksTheSameWay() throws Exception {
        guessFiveTimes(uniqueEmail(), "203.0.113.9")
                .andExpect(jsonPath("$.lockedUntil").value(LOCKED_FIFTEEN_MINUTES.toString()));
    }

    // --- Refresh and logout ---

    @Test
    void refreshRotatesTheToken() throws Exception {
        String first = refreshTokenOf(identity.login(identity.confirmedAccount(), PASSWORD));

        String second = refreshTokenOf(identity.refresh(first).andExpect(status().isOk()));
        String third = refreshTokenOf(identity.refresh(second).andExpect(status().isOk()));

        assertThat(second).isNotEqualTo(first);
        assertThat(third).isNotEqualTo(second);
    }

    @Test
    void reusingAnOldTokenRevokesTheWholeSession() throws Exception {
        String first = refreshTokenOf(identity.login(identity.confirmedAccount(), PASSWORD));
        String second = refreshTokenOf(identity.refresh(first));

        clock.advance(Duration.ofMinutes(2));

        identity.refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.refresh_reused"));
        identity.refresh(second)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_refresh_token"));
    }

    @Test
    void deviceThatLostTheResponseCanRetryWithinOneMinute() throws Exception {
        String first = refreshTokenOf(identity.login(identity.confirmedAccount(), PASSWORD));
        String lostSuccessor = refreshTokenOf(identity.refresh(first));

        clock.advance(Duration.ofSeconds(30));

        String retried = refreshTokenOf(identity.refresh(first).andExpect(status().isOk()));
        identity.refresh(retried).andExpect(status().isOk());
        identity.refresh(lostSuccessor)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_refresh_token"));
    }

    @Test
    void refreshTokenExpiresAfterThirtyDays() throws Exception {
        String token = refreshTokenOf(identity.login(identity.confirmedAccount(), PASSWORD));

        clock.advance(Duration.ofDays(31));

        identity.refresh(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_refresh_token"));
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        String token = refreshTokenOf(identity.login(identity.confirmedAccount(), PASSWORD));

        mockMvc.perform(post("/internal/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(token)))
                .andExpect(status().isNoContent());

        identity.refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void livenessStaysPublic() throws Exception {
        mockMvc.perform(get("/livez")).andExpect(status().isOk());
    }

    @Test
    void publishesOnlyThePublicHalfOfTheSigningKey() throws Exception {
        mockMvc.perform(get("/internal/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist());
    }

    /** Four wrong passwords and the fifth, which locks: its answer. */
    private ResultActions guessFiveTimes(String email, String address) throws Exception {
        for (int attempt = 1; attempt < 5; attempt++) {
            identity.login(email, WRONG + UUID.randomUUID(), null, address).andExpect(status().isUnauthorized());
        }
        return identity.login(email, WRONG, null, address)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("auth.locked"));
    }
}
