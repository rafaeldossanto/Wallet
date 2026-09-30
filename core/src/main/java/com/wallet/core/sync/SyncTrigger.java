package com.wallet.core.sync;

public enum SyncTrigger {
    /** Right after a connection is linked. */
    INITIAL,
    /** The periodic poll. Skips when the provider has nothing new. */
    SCHEDULED,
    /** The "Atualizar" button. */
    MANUAL,
    /** A provider webhook (production). */
    WEBHOOK
}
