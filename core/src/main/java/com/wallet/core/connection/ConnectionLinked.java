package com.wallet.core.connection;

import java.util.UUID;

/** Published after a connection is saved. The sync module reacts with the first sync. */
public record ConnectionLinked(UUID connectionId, UUID userId) {
}
