-- The provider's webhooks (finding A21). Pluggy does not sign them, so each one is only a hint that an
-- item changed: the Wallet keeps nothing of the body but these three fields, and always reads the
-- provider for itself. One row per event_id makes a repeated delivery harmless (INSERT ... ON
-- CONFLICT DO NOTHING); processing happens after the commit, and outcome says what came of it.
-- No foreign key and no user: an item nobody linked is recorded too (UNKNOWN_ITEM), and the rows
-- are deleted after 30 days (WebhookEventHousekeeping).
CREATE TABLE provider_webhook_events (
    id               UUID         PRIMARY KEY,
    event_id         VARCHAR(100) NOT NULL,
    event            VARCHAR(64),
    provider_item_id VARCHAR(100),
    received_at      TIMESTAMPTZ  NOT NULL,
    processed_at     TIMESTAMPTZ,
    -- SYNC_REQUESTED, SKIPPED_COOLDOWN, SKIPPED_BUSY, UNKNOWN_ITEM, ITEM_DELETED, CONSENT_EXPIRED or FAILED.
    outcome          VARCHAR(32)
);
CREATE UNIQUE INDEX ux_provider_webhook_events_event_id ON provider_webhook_events (event_id);
CREATE INDEX ix_provider_webhook_events_received_at ON provider_webhook_events (received_at);
