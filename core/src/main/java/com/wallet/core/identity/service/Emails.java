package com.wallet.core.identity.service;

import lombok.experimental.UtilityClass;

import java.util.Locale;

@UtilityClass
public class Emails {

    /** The form every e-mail is stored and compared in: trimmed and lower case. */
    public String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
