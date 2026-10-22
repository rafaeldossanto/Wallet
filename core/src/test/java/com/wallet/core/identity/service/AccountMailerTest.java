package com.wallet.core.identity.service;

import com.wallet.core.mail.OutgoingEmail;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Where the e-mailed links point, and what the account e-mails say. */
class AccountMailerTest {

    @Test
    void aDevelopmentMachineLinksToTheLocalBff() {
        assertThat(AccountMailer.resolveBaseUrl(null, false)).isEqualTo("http://localhost:8080");
        assertThat(AccountMailer.resolveBaseUrl("http://192.168.0.10:8080/", false)).isEqualTo("http://192.168.0.10:8080");
    }

    /** A link to localhost would leave every new account unconfirmed. */
    @Test
    void productionNeedsThePublicAddressOverHttps() {
        assertThatThrownBy(() -> AccountMailer.resolveBaseUrl(" ", true)).hasMessageContaining("WALLET_PUBLIC_URL");
        assertThatThrownBy(() -> AccountMailer.resolveBaseUrl("http://wallet.example.com", true)).hasMessageContaining("https");
        assertThatThrownBy(() -> AccountMailer.resolveBaseUrl("wallet.example.com", true)).hasMessageContaining("absolute");
        assertThat(AccountMailer.resolveBaseUrl("https://wallet.example.com//", true)).isEqualTo("https://wallet.example.com");
    }

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
}
