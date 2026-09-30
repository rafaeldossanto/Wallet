CREATE TABLE users (
    id                 UUID         PRIMARY KEY,
    email              VARCHAR(254) NOT NULL,
    password_hash      VARCHAR(255) NOT NULL,
    display_name       VARCHAR(80)  NOT NULL,
    failed_login_count INTEGER      NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL
);

-- The app stores e-mails in lower case, so a plain unique index is case-insensitive in practice.
CREATE UNIQUE INDEX ux_users_email ON users (email);

CREATE TABLE refresh_tokens (
    id             UUID        PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash     VARCHAR(64) NOT NULL,
    family_id      UUID        NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    rotated_at     TIMESTAMPTZ,
    replaced_by_id UUID,
    revoked_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_family_id ON refresh_tokens (family_id);
