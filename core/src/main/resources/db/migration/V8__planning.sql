-- The month's plan: salary - card bills - fixed expenses = what is left. Salary and bills come from
-- the synced data; what lives here is what the user types in.

-- Expenses that are not on a card (tuition, rent, boletos): entered once, they repeat every month.
-- day_of_month past the end of a short month falls on its last day.
CREATE TABLE fixed_expenses (
    id           UUID          PRIMARY KEY,
    user_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name         VARCHAR(80)   NOT NULL,
    amount       NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    day_of_month INTEGER       NOT NULL CHECK (day_of_month BETWEEN 1 AND 31),
    -- A provider category ("Education", "Rent"), so the app labels it like a transaction.
    category     VARCHAR(100),
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_fixed_expenses_user_id ON fixed_expenses (user_id);

-- One row per user who adjusted something. expected_salary replaces the prediction from the
-- previous month while the month's salary has not landed yet; null = predict.
CREATE TABLE planning_settings (
    user_id         UUID          PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    expected_salary NUMERIC(19,2) CHECK (expected_salary > 0),
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL
);
