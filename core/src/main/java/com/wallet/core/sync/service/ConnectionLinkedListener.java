package com.wallet.core.sync.service;

import com.wallet.core.connection.ConnectionLinked;
import com.wallet.core.sync.SyncTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * First sync right after linking, off the request thread and only once the connection row is
 * committed. Not a Modulith {@code @ApplicationModuleListener}: that one wraps the listener in a
 * transaction, and the sync must not hold one while it waits on the provider.
 */
@Component
@RequiredArgsConstructor
class ConnectionLinkedListener {

    private final SyncService syncService;

    @Async
    @TransactionalEventListener
    void on(ConnectionLinked event) {
        syncService.syncNow(event.connectionId(), SyncTrigger.INITIAL);
    }
}
