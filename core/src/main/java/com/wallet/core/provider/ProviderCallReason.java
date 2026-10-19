package com.wallet.core.provider;

/** Why the Wallet called the provider. Recorded with every call in the audit trail. */
public enum ProviderCallReason {
    /** A token for the Pluggy Connect widget, where the user picks the bank and authorizes. */
    CONNECT,
    /** Checking an item before linking it. */
    LINK,
    /** Revoking the item at the provider when the user unlinks it. */
    UNLINK,
    /** Revoking every item of a user who is deleting their account, before the data goes. */
    ACCOUNT_DELETION,
    /** The first sync, right after linking. */
    INITIAL_SYNC,
    /** The periodic poll. */
    SCHEDULED_SYNC,
    /** The "Atualizar" button. */
    MANUAL_SYNC,
    /** A provider webhook (production). */
    WEBHOOK_SYNC
}
