package com.wallet.core.provider;

/** Why the Wallet called the provider. Recorded with every call in the audit trail. */
public enum ProviderCallReason {
    /** Checking an item before linking it. */
    LINK,
    /** Revoking the item at the provider when the user unlinks it. */
    UNLINK,
    /** The first sync, right after linking. */
    INITIAL_SYNC,
    /** The periodic poll. */
    SCHEDULED_SYNC,
    /** The "Atualizar" button. */
    MANUAL_SYNC,
    /** A provider webhook (production). */
    WEBHOOK_SYNC
}
