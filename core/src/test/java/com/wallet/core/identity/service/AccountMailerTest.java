package com.wallet.core.identity.service;

import com.wallet.core.mail.OutgoingEmail;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** What the account e-mails say. */
class AccountMailerTest {

    @Test
    void theConfirmationCarriesTheLinkAndNothingAStrangerTyped() {
        OutgoingEmail email = AccountEmails.confirmAccount("rafael@example.com", "https://wallet.test/email/confirm#token=abc");

        assertThat(email.to()).isEqualTo("rafael@example.com");
        assertThat(email.text()).contains("https://wallet.test/email/confirm#token=abc").contains("24 horas");
        assertThat(email.hasUnsubscribeUrl()).isFalse();
    }

    @Test
    void theLockoutNoticeSaysUntilWhenInBrasiliaTimeAndWhoIsLockedOut() {
        Instant until = Instant.parse("2026-10-01T12:15:00Z");

        assertThat(AccountEmails.locked("rafael@example.com",
                new LoginThrottle.Lockout(LoginThrottle.Lockout.Scope.SOURCE, until)).text())
                .contains("01/10/2026 às 09:15").contains("mesma rede");
        assertThat(AccountEmails.locked("rafael@example.com",
                new LoginThrottle.Lockout(LoginThrottle.Lockout.Scope.ACCOUNT, until)).text())
                .contains("de qualquer lugar");
    }

    @Test
    void theOldAddressLearnsWhereTheAccountWent() {
        OutgoingEmail email = AccountEmails.emailChanged("old@example.com", "new@example.com", Instant.parse("2026-10-01T12:00:00Z"));

        assertThat(email.to()).isEqualTo("old@example.com");
        assertThat(email.text()).contains("new@example.com").contains("01/10/2026 às 09:00");
    }

    @Test
    void theResetLinkSaysItLastsAnHourAndThatIgnoringItChangesNothing() {
        OutgoingEmail email = AccountEmails.passwordReset("rafael@example.com", "https://wallet.test/password/reset#token=abc");

        assertThat(email.subject()).isEqualTo("Crie uma senha nova no Wallet");
        assertThat(email.text()).contains("https://wallet.test/password/reset#token=abc").contains("1 hora")
                .contains("continua a mesma");
        assertThat(email.hasUnsubscribeUrl()).isFalse();
    }

    /** No link and no address to write to: whoever did not change it is sent nowhere by this e-mail. */
    @Test
    void theChangedNoticeHasNoLinkAndPointsToTheForgotPasswordFlow() {
        OutgoingEmail email = AccountEmails.passwordChanged("rafael@example.com", Instant.parse("2026-10-01T12:00:00Z"));

        assertThat(email.subject()).isEqualTo("Sua senha do Wallet foi trocada");
        assertThat(email.text()).contains("01/10/2026 às 09:00").contains("Esqueci minha senha")
                .doesNotContain("http").doesNotContain("@");
    }
}
