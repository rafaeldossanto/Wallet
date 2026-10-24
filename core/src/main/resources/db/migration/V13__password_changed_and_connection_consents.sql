-- 1. When the password last changed (reset or change). Access tokens issued before it are refused
--    (ActiveUserInterceptor): the refresh tokens are revoked at the change, and this ends the access
--    tokens that were already out. Empty = never changed since the account exists.
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ;

-- 2. What the user agreed to when they connected a bank (LGPD L3), one row per link and per
--    renewal, and the end of that agreement.
--    connection_id has NO foreign key, on purpose: unlinking writes revoked_at and then deletes the
--    connection, and a cascade would take the proof of the revocation along with it. The record
--    belongs to the user (user_id cascades), so deleting the account still deletes it.
CREATE TABLE connection_consents (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    connection_id   UUID         NOT NULL,
    -- Which text the user agreed to (the app sends it; the screen that shows it has a version).
    purpose_version VARCHAR(32)  NOT NULL,
    -- What the Wallet reads, comma separated: ACCOUNTS,TRANSACTIONS,CREDIT_CARDS,INVESTMENTS.
    data_scopes     VARCHAR(255) NOT NULL,
    granted_at      TIMESTAMPTZ  NOT NULL,
    -- The validity the provider reported (also in connections.consent_expires_at).
    expires_at      TIMESTAMPTZ,
    -- When the user unlinked the connection.
    revoked_at      TIMESTAMPTZ
);
CREATE INDEX ix_connection_consents_connection_id ON connection_consents (connection_id);
CREATE INDEX ix_connection_consents_user_id ON connection_consents (user_id);

-- 3. connections.status gets CONSENT_EXPIRED (the authorization ended; the sync stops calling the
--    provider). The column is VARCHAR(20) with no CHECK, so nothing to change here.
