package com.wallet.bff.ratelimit;

import com.wallet.bff.config.CoreProperties;
import com.wallet.bff.config.GatewayKey;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class IpPseudonymsTest {

    private static final String KEY = "a-gateway-key-with-more-than-thirty-two-characters";

    @Test
    void sameAddressSameTextDifferentAddressDifferentText() {
        IpPseudonyms pseudonyms = pseudonyms(KEY);

        assertThat(pseudonyms.of("203.0.113.7")).isEqualTo(pseudonyms.of("203.0.113.7"));
        assertThat(pseudonyms.of("203.0.113.7")).isNotEqualTo(pseudonyms.of("203.0.113.8"));
        assertThat(pseudonyms.of("2001:db8::1")).isNotEqualTo(pseudonyms.of("203.0.113.7"));
    }

    @Test
    void twelveHexCharactersThatDoNotShowTheAddress() {
        String pseudonym = pseudonyms(KEY).of("203.0.113.7");

        assertThat(pseudonym).matches("[0-9a-f]{12}").doesNotContain("203").doesNotContain("113");
    }

    /** Without the key an address cannot be recomputed from the log: another key, another text. */
    @Test
    void theKeyDecidesTheText() {
        assertThat(pseudonyms(KEY).of("203.0.113.7"))
                .isNotEqualTo(pseudonyms(KEY + "-rotated").of("203.0.113.7"));
    }

    private static IpPseudonyms pseudonyms(String key) {
        return new IpPseudonyms(new GatewayKey(new CoreProperties(null, null, null, null, key, null), new MockEnvironment()));
    }
}
