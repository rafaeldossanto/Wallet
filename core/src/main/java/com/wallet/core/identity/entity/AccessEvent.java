package com.wallet.core.identity.entity;

/** What made an access record: a sign-in that succeeded, or a refresh token exchanged for a new one. */
public enum AccessEvent {
    LOGIN,
    REFRESH
}
