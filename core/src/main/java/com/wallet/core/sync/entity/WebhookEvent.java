package com.wallet.core.sync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A webhook the provider posted: a hint that an item changed, with nothing of the body but the
 * event's id, its name and the item's id. {@code outcome} says what the Wallet did with it.
 */
@Entity
@Table(name = "provider_webhook_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {

    private @Id UUID id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    private String event;

    @Column(name = "provider_item_id")
    private String providerItemId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    private String outcome;
}
