package com.wallet.core.identity.repository;

import com.wallet.core.identity.service.ThrottleState;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static java.util.Objects.isNull;

/**
 * The failed sign-in counters, in plain SQL: an upsert and a row lock are simpler to read here
 * than through an entity. Keyed by a hash ({@code LoginThrottle} builds it), never by an e-mail.
 */
@Repository
@RequiredArgsConstructor
public class LoginThrottles {

    private final JdbcTemplate jdbc;

    /**
     * The scope's counters with its row locked until the caller's transaction ends, so two failures
     * at once both count. A scope seen for the first time starts empty.
     */
    public ThrottleState lockForUpdate(String subjectHash, Instant now) {
        jdbc.update("""
                INSERT INTO login_throttles (subject_hash, failures, lockouts, locked_until, last_failure_at)
                VALUES (?, 0, 0, NULL, ?)
                ON CONFLICT (subject_hash) DO NOTHING
                """, subjectHash, Timestamp.from(now));
        return jdbc.queryForObject("""
                SELECT failures, lockouts, locked_until, last_failure_at
                  FROM login_throttles WHERE subject_hash = ? FOR UPDATE
                """, (row, index) -> new ThrottleState(row.getInt("failures"), row.getInt("lockouts"),
                instantOf(row.getTimestamp("locked_until")), instantOf(row.getTimestamp("last_failure_at"))), subjectHash);
    }

    public void save(String subjectHash, ThrottleState state) {
        jdbc.update("""
                UPDATE login_throttles SET failures = ?, lockouts = ?, locked_until = ?, last_failure_at = ?
                 WHERE subject_hash = ?
                """, state.failures(), state.lockouts(), timestampOf(state.lockedUntil()),
                Timestamp.from(state.lastFailureAt()), subjectHash);
    }

    /** The later lockout of the two scopes, if either is locked now. */
    public Optional<Instant> lockedUntil(String accountHash, String sourceHash, Instant now) {
        Timestamp until = jdbc.queryForObject("""
                SELECT MAX(locked_until) FROM login_throttles
                 WHERE subject_hash IN (?, ?) AND locked_until > ?
                """, Timestamp.class, accountHash, sourceHash, Timestamp.from(now));
        return Optional.ofNullable(instantOf(until));
    }

    public void delete(String subjectHash) {
        jdbc.update("DELETE FROM login_throttles WHERE subject_hash = ?", subjectHash);
    }

    /** Scopes with no failure since {@code before} and no lockout running. */
    public int deleteQuietSince(Instant before, Instant now) {
        return jdbc.update("""
                DELETE FROM login_throttles
                 WHERE last_failure_at < ? AND (locked_until IS NULL OR locked_until <= ?)
                """, Timestamp.from(before), Timestamp.from(now));
    }

    private static Instant instantOf(Timestamp timestamp) {
        return isNull(timestamp) ? null : timestamp.toInstant();
    }

    private static Timestamp timestampOf(Instant instant) {
        return isNull(instant) ? null : Timestamp.from(instant);
    }
}
