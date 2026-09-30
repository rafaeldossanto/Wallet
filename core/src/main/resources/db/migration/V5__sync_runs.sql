CREATE TABLE sync_runs (
    id                    UUID        PRIMARY KEY,
    connection_id         UUID        NOT NULL REFERENCES connections (id) ON DELETE CASCADE,
    trigger               VARCHAR(20) NOT NULL,
    status                VARCHAR(20) NOT NULL,
    error_code            VARCHAR(60),
    started_at            TIMESTAMPTZ NOT NULL,
    finished_at           TIMESTAMPTZ,
    accounts_count        INTEGER,
    transactions_upserted INTEGER,
    transactions_deleted  INTEGER
);
CREATE INDEX ix_sync_runs_connection_started ON sync_runs (connection_id, started_at DESC);

-- At most one running sync per connection, enforced by the database: a second one, from another
-- thread or another instance, fails to insert and is recorded as skipped.
CREATE UNIQUE INDEX ux_sync_runs_one_running ON sync_runs (connection_id) WHERE status = 'RUNNING';
