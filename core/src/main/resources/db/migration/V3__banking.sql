-- user_id is repeated on accounts and transactions (it is also reachable through the connection)
-- so every read filters by owner on an index, without joining another module's table.

CREATE TABLE accounts (
    id                  UUID          PRIMARY KEY,
    connection_id       UUID          NOT NULL REFERENCES connections (id) ON DELETE CASCADE,
    user_id             UUID          NOT NULL,
    provider_account_id VARCHAR(100)  NOT NULL,
    kind                VARCHAR(20)   NOT NULL,
    name                VARCHAR(200),
    number_last_digits  VARCHAR(10),
    currency_code       VARCHAR(3)    NOT NULL,
    balance             NUMERIC(19,2) NOT NULL,
    credit_limit        NUMERIC(19,2),
    available_credit    NUMERIC(19,2),
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL
);
CREATE UNIQUE INDEX ux_accounts_provider ON accounts (connection_id, provider_account_id);
CREATE INDEX ix_accounts_user_id ON accounts (user_id);

-- One balance per account per day: the Open Finance gives no balance history, so the Wallet keeps its own.
CREATE TABLE balance_snapshots (
    account_id    UUID          NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    snapshot_date DATE          NOT NULL,
    balance       NUMERIC(19,2) NOT NULL,
    PRIMARY KEY (account_id, snapshot_date)
);

CREATE TABLE transactions (
    id                      UUID          PRIMARY KEY,
    account_id              UUID          NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    user_id                 UUID          NOT NULL,
    provider_transaction_id VARCHAR(100)  NOT NULL,
    provider_reconcile_id   VARCHAR(100),
    booked_on               DATE          NOT NULL,
    -- AES-GCM ciphertext, see EncryptedStringConverter.
    description             VARCHAR(2000),
    amount                  NUMERIC(19,2) NOT NULL CHECK (amount >= 0),
    direction               VARCHAR(10)   NOT NULL,
    status                  VARCHAR(10)   NOT NULL,
    category                VARCHAR(100),
    installment_number      INTEGER,
    installment_total       INTEGER,
    provider_bill_id        VARCHAR(100),
    -- Soft delete: the provider may drop a transaction and recreate it with a new id.
    deleted_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL
);
CREATE UNIQUE INDEX ux_transactions_provider ON transactions (account_id, provider_transaction_id);
CREATE INDEX ix_transactions_account_booked ON transactions (account_id, booked_on);
CREATE INDEX ix_transactions_user_booked ON transactions (user_id, booked_on) WHERE deleted_at IS NULL;

CREATE TABLE credit_card_bills (
    id               UUID          PRIMARY KEY,
    account_id       UUID          NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    provider_bill_id VARCHAR(100)  NOT NULL,
    due_date         DATE          NOT NULL,
    closing_date     DATE,
    total_amount     NUMERIC(19,2) NOT NULL,
    minimum_payment  NUMERIC(19,2),
    currency_code    VARCHAR(3)    NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL
);
CREATE UNIQUE INDEX ux_credit_card_bills_provider ON credit_card_bills (account_id, provider_bill_id);
