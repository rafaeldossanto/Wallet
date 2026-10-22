package com.wallet.bff.client;

/**
 * The core's routes, grouped by how they fail. Each group has a circuit breaker and a bulkhead of
 * its own ({@link CoreCallGuards}): a slow group opens only its own circuit and fills only its own
 * slots, so a Pluggy that stopped answering does not take the statement down with it.
 */
public enum CoreRouteGroup {

    /** Register, login, refresh, logout: each login is a BCrypt in the core. */
    AUTH,

    /** Plain reads and small writes, answered from the core's indexes. */
    READS,

    /** What the core adds up over long periods or several modules: overview, history, insights, planning. */
    SCREENS,

    /** The statement's text search: the core decrypts the period's descriptions to match them. */
    SEARCH,

    /**
     * Where the core talks to Pluggy: link, unlink, connect token and account deletion wait on it
     * before answering, and a manual sync starts a read of the bank.
     */
    PROVIDER
}
