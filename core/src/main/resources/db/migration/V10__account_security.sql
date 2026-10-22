-- Account security: e-mail confirmation (finding A16), a second factor (TOTP), a lockout that a
-- stranger cannot aim at someone else's account (A5) and the e-mail change of the profile (L11).

-- A new account waits for its e-mail to be confirmed before anyone signs in with it. The accounts
-- created before this existed count as confirmed.
ALTER TABLE users ADD COLUMN email_confirmed_at TIMESTAMPTZ;
UPDATE users SET email_confirmed_at = created_at;
-- Unconfirmed accounts are deleted after a few days (AccountHousekeeping).
CREATE INDEX ix_users_unconfirmed ON users (created_at) WHERE email_confirmed_at IS NULL;

-- Second factor, TOTP (RFC 6238). The secret is encrypted with the data key (DataCipher), like the
-- transaction descriptions. A secret without totp_enabled_at is a set-up waiting for its first code.
ALTER TABLE users ADD COLUMN totp_secret VARCHAR(255);
ALTER TABLE users ADD COLUMN totp_enabled_at TIMESTAMPTZ;
-- The last 30-second step accepted: a code works once, even inside its window.
ALTER TABLE users ADD COLUMN totp_last_step BIGINT;

-- The lockout moves to login_throttles, where it is kept per e-mail and per e-mail + client address.
ALTER TABLE users DROP COLUMN failed_login_count;
ALTER TABLE users DROP COLUMN locked_until;

-- One-time links sent by e-mail: confirm a new account, or confirm the new address of an account.
-- Only the SHA-256 of the token is kept, like the refresh tokens.
CREATE TABLE email_tokens (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    VARCHAR(32)  NOT NULL CHECK (purpose IN ('CONFIRM_ACCOUNT', 'CHANGE_EMAIL')),
    token_hash VARCHAR(64)  NOT NULL,
    -- Only for CHANGE_EMAIL: the address the account moves to once the link is opened.
    new_email  VARCHAR(254),
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX ux_email_tokens_token_hash ON email_tokens (token_hash);
CREATE INDEX ix_email_tokens_user_purpose ON email_tokens (user_id, purpose, created_at);

-- Single-use recovery codes of the second factor, as SHA-256 (each code has 80 random bits).
CREATE TABLE recovery_codes (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_recovery_codes_user_id ON recovery_codes (user_id);

-- Failed sign-ins. No foreign key and no e-mail in clear: e-mails without an account get rows too,
-- so a lockout answers the same whether the account exists or not. subject_hash is the SHA-256 of
-- 'account:<e-mail>' (every address) or 'source:<e-mail>|<client address>'. Rows go a day after
-- their last failure (AccountHousekeeping).
CREATE TABLE login_throttles (
    subject_hash    VARCHAR(64) PRIMARY KEY,
    failures        INTEGER     NOT NULL,
    -- How many times it locked in a row: each lockout lasts twice the previous one.
    lockouts        INTEGER     NOT NULL,
    locked_until    TIMESTAMPTZ,
    last_failure_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_login_throttles_last_failure_at ON login_throttles (last_failure_at);

-- When each notice last went to an account, so nobody can flood an inbox with them.
CREATE TABLE email_notices (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind    VARCHAR(32) NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, kind)
);
