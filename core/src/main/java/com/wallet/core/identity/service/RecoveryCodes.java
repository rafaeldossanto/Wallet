package com.wallet.core.identity.service;

import com.wallet.core.shared.security.OpaqueTokens;
import lombok.experimental.UtilityClass;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The single-use codes that stand in for the authenticator app (a lost or reset phone).
 *
 * <p>16 characters of Crockford's base32 (no I, L, O or U, nothing to confuse), 80 random bits,
 * shown as {@code ABCD-EFGH-JKMN-PQRS}. With that much entropy a plain SHA-256 is enough to store
 * them, the same reasoning as the refresh tokens: there is nothing to brute-force.
 */
@UtilityClass
public class RecoveryCodes {

    public static final int COUNT = 10;

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int LENGTH = 16;
    private static final int GROUP = 4;
    private static final Pattern TOTP_CODE = Pattern.compile("\\d{6}");
    private static final SecureRandom RANDOM = new SecureRandom();

    public List<String> generate() {
        List<String> codes = new ArrayList<>(COUNT);
        for (int index = 0; index < COUNT; index++) {
            StringBuilder code = new StringBuilder(LENGTH + LENGTH / GROUP);
            for (int position = 0; position < LENGTH; position++) {
                if (position > 0 && position % GROUP == 0) {
                    code.append('-');
                }
                code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            codes.add(code.toString());
        }
        return codes;
    }

    /**
     * The code as stored, whatever way it was typed: case, hyphens and spaces do not matter, and the
     * letters people read as digits (O, I, L) count as those digits.
     */
    public String normalize(String typed) {
        return typed.toUpperCase(Locale.ROOT)
                .replaceAll("[\\s-]", "")
                .replace('O', '0')
                .replace('I', '1')
                .replace('L', '1');
    }

    public String hash(String typed) {
        return OpaqueTokens.hash(normalize(typed));
    }

    /** Six digits are an authenticator code; anything else is tried as a recovery code. */
    public boolean isAuthenticatorCode(String typed) {
        return TOTP_CODE.matcher(typed.replaceAll("\\s", "")).matches();
    }
}
