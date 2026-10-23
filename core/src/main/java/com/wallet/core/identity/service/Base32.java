package com.wallet.core.identity.service;

import lombok.experimental.UtilityClass;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/**
 * RFC 4648 base32, the way authenticator apps take a TOTP secret: upper case, no padding. Java has
 * base64 only.
 */
@UtilityClass
public class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public String encode(byte[] bytes) {
        StringBuilder text = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xFF);
            bits += 8;
            while (bits >= 5) {
                text.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            text.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return text.toString();
    }

    /** Ignores case, spaces and padding, as people type a secret by hand. */
    public byte[] decode(String text) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char character : text.toUpperCase(Locale.ROOT).toCharArray()) {
            if (character == '=' || Character.isWhitespace(character)) {
                continue;
            }
            int value = ALPHABET.indexOf(character);
            if (value < 0) {
                throw new IllegalArgumentException("Not base32: " + character);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                bytes.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return bytes.toByteArray();
    }
}
