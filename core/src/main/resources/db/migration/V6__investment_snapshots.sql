-- Like balance_snapshots: providers only report today's value, so the Wallet keeps the history
-- that feeds the net worth chart. A closed position gets a zero on the day it closed, so it is
-- not carried forward forever.
CREATE TABLE investment_snapshots (
    investment_id UUID          NOT NULL REFERENCES investments (id) ON DELETE CASCADE,
    snapshot_date DATE          NOT NULL,
    balance       NUMERIC(19,2) NOT NULL,
    PRIMARY KEY (investment_id, snapshot_date)
);
