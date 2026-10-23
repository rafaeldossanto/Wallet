package com.wallet.core.mail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailAddressesTest {

    @Test
    void keepsTheFirstLetterAndTheDomain() {
        assertThat(EmailAddresses.mask("rafael@example.com")).isEqualTo("r*****@example.com");
        assertThat(EmailAddresses.mask("ra@example.com")).isEqualTo("r***@example.com");
    }

    @Test
    void somethingThatIsNotAnAddressShowsNothing() {
        assertThat(EmailAddresses.mask("@example.com")).isEqualTo("***");
        assertThat(EmailAddresses.mask("rafael")).isEqualTo("***");
    }
}
