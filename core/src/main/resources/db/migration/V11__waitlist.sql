-- The landing page's waitlist ("Quero ser avisado"): double opt-in by e-mail, one-click unsubscribe
-- (the row is deleted), and the opening announcement (WaitlistAnnouncer).

CREATE TABLE waitlist_entries (
    id                       UUID         PRIMARY KEY,
    -- Trimmed and lower case, in clear like users.email: the announcement is sent to it.
    email                    VARCHAR(320) NOT NULL,
    status                   VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED')),
    -- Which text the person agreed to when they signed up (the server sets it, the client never does).
    consent_version          VARCHAR(32)  NOT NULL,
    created_at               TIMESTAMPTZ  NOT NULL,
    confirmed_at             TIMESTAMPTZ,
    -- The link of the confirmation e-mail, as SHA-256. Kept after the confirmation, so opening the
    -- same link twice is not an error; a new link replaces it while the entry is PENDING.
    confirm_token_hash       VARCHAR(64),
    confirm_token_expires_at TIMESTAMPTZ,
    -- When the last confirmation e-mail went: at most one an hour.
    confirm_sent_at          TIMESTAMPTZ,
    -- Born with the confirmation. In clear, unlike the other tokens: every announcement has to
    -- rebuild the link of its recipient, and the token only lets someone delete that one row.
    unsubscribe_token        VARCHAR(64),
    -- The last announcement sent to this person, so running the command again resends nothing.
    last_announcement        VARCHAR(64),
    last_announced_at        TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_waitlist_entries_email ON waitlist_entries (email);
CREATE UNIQUE INDEX ux_waitlist_entries_confirm_token_hash ON waitlist_entries (confirm_token_hash);
CREATE UNIQUE INDEX ux_waitlist_entries_unsubscribe_token ON waitlist_entries (unsubscribe_token);
-- Entries nobody confirmed are deleted after a few days (WaitlistHousekeeping).
CREATE INDEX ix_waitlist_entries_pending ON waitlist_entries (created_at) WHERE status = 'PENDING';
