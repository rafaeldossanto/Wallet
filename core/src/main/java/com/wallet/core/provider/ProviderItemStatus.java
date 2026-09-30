package com.wallet.core.provider;

public enum ProviderItemStatus {
    /** Last sync finished; data is ready to read. */
    READY,
    /** The provider is collecting data right now. */
    UPDATING,
    /** The user has to act: log in again, approve at the bank, renew the consent. */
    NEEDS_USER_ACTION,
    /** Last sync failed on the provider side; it may work on the next attempt. */
    FAILED
}
