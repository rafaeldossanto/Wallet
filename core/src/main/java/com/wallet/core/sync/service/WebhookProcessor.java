package com.wallet.core.sync.service;

import com.wallet.core.connection.ConnectionRegistry;
import com.wallet.core.connection.LinkedConnection;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.sync.SyncTrigger;
import com.wallet.core.sync.WebhookReceived;
import com.wallet.core.sync.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;

import static java.util.Objects.isNull;

/**
 * What a webhook means for the Wallet, after the commit that recorded it and off the request
 * thread. The provider's word is a hint and nothing more:
 *
 * <ul>
 *   <li>an item nobody linked: recorded, nothing else;</li>
 *   <li>an item deleted at the provider: the connection needs the user's attention (the status that
 *       already exists for it);</li>
 *   <li>anything else on a known item: a sync with the {@code WEBHOOK} trigger, under the rules of
 *       the "Atualizar" button ({@link ManualSyncService}): the cooldown, the bulkhead and one sync
 *       per connection. A refusal is not an error: the poll reads the connection later; an ended
 *       authorization reads nothing.</li>
 * </ul>
 *
 * <p>The sync itself reads the provider for the item's real state. Logs carry ids only, never the
 * event's content.
 */
@Slf4j
@Component
public class WebhookProcessor {

    private final ConnectionRegistry registry;
    private final ManualSyncService manualSync;
    private final WebhookEventRepository events;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public WebhookProcessor(ConnectionRegistry registry, ManualSyncService manualSync, WebhookEventRepository events,
                            Clock clock, PlatformTransactionManager transactionManager) {
        this.registry = registry;
        this.manualSync = manualSync;
        this.events = events;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** Off the request thread and only once the event row is committed (like the first sync after linking). */
    @Async
    @TransactionalEventListener
    void on(WebhookReceived received) {
        String outcome;
        try {
            outcome = handle(received);
        } catch (RuntimeException ex) {
            log.error("[WEBHOOK] Event {} failed unexpectedly", received.id(), ex);
            outcome = WebhookOutcomes.FAILED;
        }
        String recorded = outcome;
        transactions.executeWithoutResult(status -> events.recordOutcome(received.id(), recorded, clock.instant()));
        log.info("[WEBHOOK] Event {} handled: {}", received.id(), outcome);
    }

    String handle(WebhookReceived received) {
        Optional<LinkedConnection> connection = isNull(received.itemId())
                ? Optional.empty()
                : registry.findByProviderItemId(received.itemId());
        if (connection.isEmpty()) {
            return WebhookOutcomes.UNKNOWN_ITEM;
        }
        LinkedConnection linked = connection.get();
        if ("item/deleted".equals(received.event())) {
            registry.recordNeedsAttention(linked.id());
            return WebhookOutcomes.ITEM_DELETED;
        }
        try {
            manualSync.request(linked.userId(), linked.id(), SyncTrigger.WEBHOOK);
            return WebhookOutcomes.SYNC_REQUESTED;
        } catch (DomainException ex) {
            return WebhookOutcomes.ofRefusal(ex.getCode());
        }
    }
}
