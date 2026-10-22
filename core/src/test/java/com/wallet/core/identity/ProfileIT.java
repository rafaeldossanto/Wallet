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
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import static com.wallet.core.RecordingMailTransport.tokenIn;
import static com.wallet.core.identity.IdentityClient.PASSWORD;
import static com.wallet.core.identity.IdentityClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Editing the profile (LGPD L11): the name freely, the e-mail with the password and the new inbox. */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class, TestMailConfiguration.class})
class ProfileIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MutableClock clock;

    @Autowired
    private RecordingMailTransport mail;

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
    void renamesTheAccount() throws Exception {
        rename("  Rafael Santos ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Rafael Santos"));

        identity.me(accessToken).andExpect(jsonPath("$.displayName").value("Rafael Santos"));
    }

    @Test
    void aNameIsRequired() throws Exception {
        rename("   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("displayName"));
    }

    @Test
    void theEmailMovesOnlyOnceTheNewAddressConfirms() throws Exception {
        String newEmail = uniqueEmail();

        changeEmail(newEmail.toUpperCase(), PASSWORD).andExpect(status().isAccepted()).andExpect(content().string(""));

        identity.me(accessToken)
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.pendingEmail").value(newEmail));
        identity.login(email, PASSWORD).andExpect(status().isOk());

        OutgoingEmail link = mail.await(newEmail);
        assertThat(link.subject()).isEqualTo("Confirme seu novo e-mail no Wallet");
        identity.confirm(tokenIn(link))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("EMAIL_CHANGED"));

        OutgoingEmail notice = mail.awaitNth(email, 2);
        assertThat(notice.subject()).isEqualTo("O e-mail da sua conta do Wallet foi trocado");
        assertThat(notice.text()).contains(newEmail).contains("01/10/2026 às 09:00");
        identity.me(accessToken)
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.pendingEmail").doesNotExist());
        identity.login(newEmail, PASSWORD).andExpect(status().isOk());
        identity.login(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void changingTheEmailTakesThePassword() throws Exception {
        String newEmail = uniqueEmail();

        changeEmail(newEmail, "not the password")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.wrong_password"));

        mail.assertNothingMoreTo(newEmail, 0);
        identity.me(accessToken).andExpect(jsonPath("$.pendingEmail").doesNotExist());
    }

    /** As at sign-up: the answer does not tell whether the address has an account; its owner is told. */
    @Test
    void anAddressWithAnAccountAnswersTheSameAndItsOwnerIsTold() throws Exception {
        String taken = identity.confirmedAccount();

        changeEmail(taken, PASSWORD).andExpect(status().isAccepted());

        identity.me(accessToken).andExpect(jsonPath("$.pendingEmail").value(taken));
        OutgoingEmail notice = mail.awaitNth(taken, 2);
        assertThat(notice.subject()).isEqualTo("Tentativa de cadastro no Wallet");
        assertThat(notice.text()).doesNotContain("#token=");
    }

    @Test
    void anAddressTakenAfterTheLinkWentStaysWithItsAccount() throws Exception {
        String newEmail = uniqueEmail();
        changeEmail(newEmail, PASSWORD).andExpect(status().isAccepted());
        String link = tokenIn(mail.await(newEmail));
        identity.register(newEmail, PASSWORD).andExpect(status().isAccepted());
        identity.confirm(tokenIn(mail.awaitNth(newEmail, 2))).andExpect(status().isOk());

        identity.confirm(link)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.email_taken"));
        identity.me(accessToken).andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void theSameEmailIsNotAChange() throws Exception {
        changeEmail(email, PASSWORD)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("auth.email_unchanged"));
    }

    private ResultActions rename(String displayName) throws Exception {
        return identity.json(put("/internal/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken),
                "{\"displayName\":\"%s\"}".formatted(displayName));
    }

    private ResultActions changeEmail(String newEmail, String password) throws Exception {
        return identity.json(post("/internal/me/email").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken),
                "{\"newEmail\":\"%s\",\"password\":\"%s\"}".formatted(newEmail, password));
    }
}
