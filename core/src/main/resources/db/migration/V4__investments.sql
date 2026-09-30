CREATE TABLE investments (
    id                     UUID          PRIMARY KEY,
    connection_id          UUID          NOT NULL REFERENCES connections (id) ON DELETE CASCADE,
    user_id                UUID          NOT NULL,
    provider_investment_id VARCHAR(100)  NOT NULL,
    kind                   VARCHAR(20)   NOT NULL,
    subtype                VARCHAR(40),
    name                   VARCHAR(300),
    currency_code          VARCHAR(3)    NOT NULL,
    balance                NUMERIC(19,2) NOT NULL,
    amount_invested        NUMERIC(19,2),
    due_date               DATE,
    -- Set when the position was fully withdrawn or disappeared from the provider.
    closed_at              TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL
);
CREATE UNIQUE INDEX ux_investments_provider ON investments (connection_id, provider_investment_id);
CREATE INDEX ix_investments_user_open ON investments (user_id) WHERE closed_at IS NULL;
