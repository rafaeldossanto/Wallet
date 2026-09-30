CREATE TABLE connections (
    id                    UUID         PRIMARY KEY,
    -- Deleting a user (LGPD) takes their connections, and with them every synced row.
    user_id               UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider              VARCHAR(20)  NOT NULL,
    provider_item_id      VARCHAR(100) NOT NULL,
    institution_name      VARCHAR(120),
    institution_image_url VARCHAR(500),
    status                VARCHAR(20)  NOT NULL,
    provider_updated_at   TIMESTAMPTZ,
    last_synced_at        TIMESTAMPTZ,
    consent_expires_at    TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL
);

-- One provider item belongs to exactly one Wallet connection.
CREATE UNIQUE INDEX ux_connections_provider_item ON connections (provider, provider_item_id);
CREATE INDEX ix_connections_user_id ON connections (user_id);
