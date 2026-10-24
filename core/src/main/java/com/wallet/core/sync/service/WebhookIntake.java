package com.wallet.core.sync.service;

import com.wallet.core.sync.WebhookReceived;
import com.wallet.core.sync.dto.PluggyWebhookRequest;
import com.wallet.core.sync.repository.WebhookEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;

/**
 * Takes in a webhook: records it once and hands it to {@link WebhookProcessor}. A repeated delivery
 * of the same {@code eventId} finds the row and does nothing, so the answer is the same 202 and no
 * second sync is asked for. Nothing here waits on the provider or on a sync.
 */
@Slf4j
@Service
public class WebhookIntake {

    private final WebhookEventRepository events;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public WebhookIntake(WebhookEventRepository events, ApplicationEventPublisher publisher, Clock clock,
                         PlatformTransactionManager transactionManager) {
        this.events = events;
        this.publisher = publisher;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void receive(PluggyWebhookRequest request) {
        transactions.executeWithoutResult(status -> {
            UUID id = UUID.randomUUID();
            if (events.insertIfNew(id, request.eventId(), request.event(), request.itemId(), clock.instant()) == 0) {
                log.info("[WEBHOOK] Event repeated, nothing to do");
                return;
            }
            publisher.publishEvent(new WebhookReceived(id, request.event(), request.itemId()));
        });
    }
}
