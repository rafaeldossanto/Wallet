-- 1. Access records (Marco Civil da Internet, art. 15: the application keeps the records of access,
--    in secrecy, for 6 months). One row per successful login and per refresh.
--    user_id has NO foreign key, on purpose: the record has to outlive the account for its 6 months
--    (a legal obligation, LGPD art. 16, I), so deleting the user must not take it along.
--    ip is the client address as the BFF saw it, encrypted with the data key (DataCipher): a database
--    dump does not hand out who connected from where. No route reads this table; it is opened only by
--    a court order, straight in the database.
CREATE TABLE access_records (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL,
    event       VARCHAR(16) NOT NULL CHECK (event IN ('LOGIN', 'REFRESH')),
    ip          TEXT        NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_access_records_occurred_at ON access_records (occurred_at);
CREATE INDEX ix_access_records_user_id ON access_records (user_id, occurred_at);

-- 2. Every query on investments filters by user, closed positions included (the export, the history);
--    until now only the partial index of the open ones existed.
CREATE INDEX ix_investments_user_id ON investments (user_id);
