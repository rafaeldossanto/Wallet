-- Fixed expenses suggested from the bank debits that repeat every month. The suggestions are
-- computed from the transactions on every read; what lives here is the ones the user dismissed,
-- so they do not come back.

-- description_key is a keyed fingerprint (HMAC, see DataCipher.fingerprint) of the debits'
-- description words, never the description itself, which is encrypted in transactions.
-- day_of_month tells two series with the same description apart (two boletos with the same
-- generic name); a dismissal covers a few days around it, since a debit's usual day can move.
CREATE TABLE dismissed_fixed_expense_suggestions (
    id              UUID          PRIMARY KEY,
    user_id         UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    description_key VARCHAR(64)   NOT NULL,
    day_of_month    INTEGER       NOT NULL CHECK (day_of_month BETWEEN 1 AND 31),
    created_at      TIMESTAMPTZ   NOT NULL
);
CREATE UNIQUE INDEX ux_dismissed_fixed_expense_suggestions
    ON dismissed_fixed_expense_suggestions (user_id, description_key, day_of_month);
