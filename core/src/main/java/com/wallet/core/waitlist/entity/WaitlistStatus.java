package com.wallet.core.waitlist.entity;

public enum WaitlistStatus {
    /** Signed up; the confirmation link was not opened yet. */
    PENDING,
    /** Opened the link: this address is on the list. */
    CONFIRMED
}
