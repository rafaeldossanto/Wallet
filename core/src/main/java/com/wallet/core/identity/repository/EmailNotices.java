package com.wallet.core.identity.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** When each kind of notice last went to an account. */
@Repository
@RequiredArgsConstructor
public class EmailNotices {

    private final JdbcTemplate jdbc;

    /**
     * Takes the turn to send this notice: true when none went since {@code sentBefore}, and then it
     * counts as sent now. One statement, so two requests at once cannot both get the turn.
     */
    public boolean claim(UUID userId, String kind, Instant now, Instant sentBefore) {
        return jdbc.update("""
                INSERT INTO email_notices (user_id, kind, sent_at) VALUES (?, ?, ?)
                ON CONFLICT (user_id, kind) DO UPDATE SET sent_at = EXCLUDED.sent_at
                 WHERE email_notices.sent_at <= ?
                """, userId, kind, Timestamp.from(now), Timestamp.from(sentBefore)) > 0;
    }
}
