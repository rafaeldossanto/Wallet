-- Audit trail: one row per HTTP call the core makes to the data provider. When, for whom, for which
-- connection, what and why, and how it went. Never amounts, descriptions, tokens or credentials.
-- No foreign keys on purpose: the record of an unlink, or of a deleted user's calls, has to outlive
-- the rows it names. Rows older than wallet.provider.audit.retention (180 days) are deleted nightly.
CREATE TABLE provider_calls (
    id               UUID         PRIMARY KEY,
    called_at        TIMESTAMPTZ  NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    operation        VARCHAR(40)  NOT NULL,
    reason           VARCHAR(20),
    user_id          UUID,
    connection_id    UUID,
    provider_item_id VARCHAR(100),
    outcome          VARCHAR(10)  NOT NULL,
    http_status      INTEGER,
    error_code       VARCHAR(60),
    duration_ms      INTEGER      NOT NULL
);
CREATE INDEX ix_provider_calls_called_at ON provider_calls (called_at);
CREATE INDEX ix_provider_calls_connection ON provider_calls (connection_id, called_at);
