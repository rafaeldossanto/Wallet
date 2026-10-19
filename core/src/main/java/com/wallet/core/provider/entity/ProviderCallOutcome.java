package com.wallet.core.provider.entity;

public enum ProviderCallOutcome {
    /** The provider answered with a status below 400. */
    SUCCESS,
    /** An error status, or no answer at all. */
    FAILURE
}
