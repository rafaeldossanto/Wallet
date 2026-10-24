package com.wallet.core.connection;

import java.util.UUID;

/**
 * Published after the user renewed the authorization of a connection and it came back to ACTIVE. The
 * sync module reacts like it does to the "Atualizar" button: with its cooldown and its bulkhead.
 */
public record ConnectionRenewed(UUID connectionId, UUID userId) {
}
