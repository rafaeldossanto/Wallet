package com.wallet.core.connection;

public enum ConnectionStatus {
    /** Linked and syncing normally. */
    ACTIVE,
    /** A sync is running right now. */
    SYNCING,
    /** The bank needs the user: new login, consent renewal or approval. Scheduled syncs skip it. */
    NEEDS_ATTENTION,
    /** The authorization (Open Finance consent) ended: nothing is read at the provider until the user renews it. */
    CONSENT_EXPIRED
}
