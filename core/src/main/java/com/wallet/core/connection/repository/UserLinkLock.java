package com.wallet.core.connection.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * One link at a time per user. Without it, parallel requests would all count the user's
 * connections before any of them commits, and each would get past the ceiling.
 *
 * <p>A transaction-level advisory lock: there is no row to lock before the user's first connection,
 * and the users table belongs to another module. Released at commit or rollback; other users never
 * wait on it. The two-key form keeps it apart from Flyway's single-key lock.
 */
@Repository
@RequiredArgsConstructor
public class UserLinkLock {

    /** First key of the pair: "LINK" in ASCII. */
    private static final int NAMESPACE = 0x4C494E4B;

    private final JdbcTemplate jdbc;

    /** Joins the caller's transaction and refuses to run without one: alone, the lock would end at once. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(UUID userId) {
        jdbc.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(?, hashtext(?))", Integer.class,
                NAMESPACE, userId.toString());
    }
}
