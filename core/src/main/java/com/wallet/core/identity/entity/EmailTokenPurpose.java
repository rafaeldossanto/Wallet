package com.wallet.core.identity.entity;

public enum EmailTokenPurpose {

    /** Sent at sign-up: opening it activates the account. */
    CONFIRM_ACCOUNT,

    /** Sent to the new address of an account: opening it moves the account there. */
    CHANGE_EMAIL,

    /** Sent to a confirmed account that forgot its password: opening it, with a new password, replaces the old one. */
    PASSWORD_RESET
}
