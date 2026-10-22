package com.wallet.core.identity.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base32Test {

    /** RFC 4648, section 10, without the padding authenticator apps leave out. */
    @ParameterizedTest
    @CsvSource({
            "f, MY",
            "fo, MZXQ",
            "foo, MZXW6",
            "foob, MZXW6YQ",
            "fooba, MZXW6YTB",
            "foobar, MZXW6YTBOI"
    })
    void encodesAndDecodesTheRfcVectors(String text, String encoded) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);

        assertThat(Base32.encode(bytes)).isEqualTo(encoded);
        assertThat(Base32.decode(encoded)).isEqualTo(bytes);
    }

    @Test
    void decodesWhatPeopleTypeByHand() {
        assertThat(Base32.decode("mzxw 6ytb oi======")).isEqualTo("foobar".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> Base32.decode("MZXW1")).isInstanceOf(IllegalArgumentException.class);
    }
}
