package com.wallet.core.connection;

import java.util.Set;
import java.util.UUID;

/**
 * The connection module's API for deleting an account: every connection is let go of at the
 * provider while the Wallet still knows which items it has. Deleting the user afterwards cascades
 * over the connection rows, and with them the only reference to those items.
 */
public interface ConnectionRevocation {

    /**
     * For each connection of the user not in {@code skip}, revokes the item at the provider the way
     * unlinking does: always in production, and while developing only items the widget created for
     * this user (a Meu Pluggy item belongs to the Meu Pluggy account and is only let go of).
     *
     * <p>Best effort: a provider failure is logged and counted, and the next connection is tried.
     * Never throws because of the provider. Does not delete anything in the Wallet.
     */
    RevocationReport revokeAll(UUID userId, Set<UUID> skip);
}
