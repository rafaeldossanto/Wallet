package com.wallet.core.sync.service;

import com.wallet.core.connection.ConnectionLinked;
import com.wallet.core.connection.ConnectionRenewed;
import com.wallet.core.sync.SyncTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * First sync right after linking (and the update that follows a renewal), off the request thread and only once the connection row is
 * committed. Not a Modulith {@code @ApplicationModuleListener}: that one wraps the listener in a
 * transaction, and the sync must not hold one while it waits on the provider.
 */
@Component
@RequiredArgsConstructor
class ConnectionLinkedListener {

    private final SyncService syncService;
    private final ManualSyncService manualSyncService;

    @Async
    @TransactionalEventListener
    void on(ConnectionLinked event) {
        syncService.syncNow(event.connectionId(), SyncTrigger.INITIAL);
    }

    /**
     * After a renewal the first read is the user's update: the manual rules apply (cooldown and
     * bulkhead), so the renewal cannot be used to read the bank more often than the button allows.
     * What they refuse waits for the next poll.
     */
    @Async
    @TransactionalEventListener
    void on(ConnectionRenewed event) {
        manualSyncService.afterRenewal(event.userId(), event.connectionId());
    }
}
