package com.wallet.core.identity.controller;

import lombok.experimental.UtilityClass;

import java.util.Locale;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;

/**
 * Where a request came from, as the BFF saw it. The core is only reached through the BFF (with the
 * gateway key), so this header is the BFF's word and never the client's. It scopes the sign-in
 * lockout to one address ({@code LoginThrottle}).
 */
@UtilityClass
public class ClientAddress {

    public static final String HEADER = "X-Wallet-Client-Address";

    /** Without the header (a test calling the core directly) every request shares one scope. */
    static final String UNKNOWN = "unknown";

    /** IPv4 or IPv6 and nothing else: it ends up hashed into a key, never anywhere it could be read back. */
    private static final Pattern ADDRESS = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    public String of(String header) {
        if (isNull(header) || !ADDRESS.matcher(header.strip()).matches()) {
            return UNKNOWN;
        }
        return header.strip().toLowerCase(Locale.ROOT);
    }
}
