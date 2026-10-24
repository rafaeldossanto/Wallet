package com.wallet.core.sync.repository;

import com.wallet.core.sync.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    /** The provider delivers the same event more than once: the first insert returns 1, the others 0. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into provider_webhook_events (id, event_id, event, provider_item_id, received_at)
            values (:id, :eventId, :event, :itemId, :receivedAt)
            on conflict (event_id) do nothing
            """, nativeQuery = true)
    int insertIfNew(@Param("id") UUID id, @Param("eventId") String eventId, @Param("event") String event,
                    @Param("itemId") String itemId, @Param("receivedAt") Instant receivedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update WebhookEvent e set e.outcome = :outcome, e.processedAt = :now where e.id = :id")
    int recordOutcome(@Param("id") UUID id, @Param("outcome") String outcome, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WebhookEvent e where e.receivedAt < :before")
    int deleteReceivedBefore(@Param("before") Instant before);
}
