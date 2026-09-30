package com.wallet.core.connection;

public enum ConnectionStatus {
    /** Linked and syncing normally. */
    ACTIVE,
    /** A sync is running right now. */
    SYNCING,
    /** The bank needs the user: new login, consent renewal or approval. Scheduled syncs skip it. */
    NEEDS_ATTENTION
}
