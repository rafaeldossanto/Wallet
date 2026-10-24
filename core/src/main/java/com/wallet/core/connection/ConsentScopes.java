package com.wallet.core.connection;

import lombok.experimental.UtilityClass;

/**
 * What the Wallet reads from a connected bank, as the user is told before connecting ("Antes de
 * conectar" in the app) and as recorded with each consent. A new kind of data is a new purpose
 * version, not a silent change to this list.
 */
@UtilityClass
public class ConsentScopes {

    public static final String ACCOUNTS = "ACCOUNTS";
    public static final String TRANSACTIONS = "TRANSACTIONS";
    public static final String CREDIT_CARDS = "CREDIT_CARDS";
    public static final String INVESTMENTS = "INVESTMENTS";

    /** The scopes of {@code open-finance-v1}, in the form stored in {@code connection_consents.data_scopes}. */
    public String current() {
        return String.join(",", ACCOUNTS, TRANSACTIONS, CREDIT_CARDS, INVESTMENTS);
    }
}
