package com.wallet.core.identity;

import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.identity.service.AccountHousekeeping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The nightly clean-up, run the way the scheduler runs it: the task {@code configureTasks}
 * registers, which calls the object itself and not the Spring proxy. Through the proxy it always
 * worked; scheduled, it failed every night for want of a transaction and deleted nothing.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountHousekeepingIT {

    private static final Instant LONG_AGO = Instant.parse("2020-01-01T00:00:00Z");

    private static final String USER = "select count(*) from users where id = ?";
    private static final String EMAIL_TOKEN = "select count(*) from email_tokens where id = ?";
    private static final String THROTTLE = "select count(*) from login_throttles where subject_hash = ?";

    @Autowired
    private AccountHousekeeping housekeeping;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theScheduledRunDeletesWhatIdentityNoLongerNeeds() {
        UUID neverConfirmed = insertUser(null, LONG_AGO);
        UUID justSignedUp = insertUser(null, Instant.now());
        UUID confirmed = insertUser(LONG_AGO, LONG_AGO);
        UUID expiredLink = insertEmailToken(confirmed, LONG_AGO);
        UUID openLink = insertEmailToken(confirmed, Instant.now().plusSeconds(3600));
        String quietCounter = insertThrottle(LONG_AGO);
        String recentCounter = insertThrottle(Instant.now());

        runTheScheduledTask();

        assertThat(exists(USER, neverConfirmed)).isFalse();
        assertThat(exists(USER, justSignedUp)).isTrue();
        assertThat(exists(USER, confirmed)).isTrue();
        assertThat(exists(EMAIL_TOKEN, expiredLink)).isFalse();
        assertThat(exists(EMAIL_TOKEN, openLink)).isTrue();
        assertThat(exists(THROTTLE, quietCounter)).isFalse();
        assertThat(exists(THROTTLE, recentCounter)).isTrue();
    }

    private void runTheScheduledTask() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        housekeeping.configureTasks(registrar);
        assertThat(registrar.getCronTaskList()).hasSize(1);
        registrar.getCronTaskList().getFirst().getRunnable().run();
    }

    private UUID insertUser(Instant confirmedAt, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into users (id, email, password_hash, display_name, email_confirmed_at, created_at)
                values (?, ?, 'hash', 'Test', ?, ?)
                """, id, "housekeeping+" + id + "@example.com", timestamp(confirmedAt), timestamp(createdAt));
        return id;
    }

    private UUID insertEmailToken(UUID userId, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into email_tokens (id, user_id, purpose, token_hash, expires_at, created_at)
                values (?, ?, 'CONFIRM_ACCOUNT', ?, ?, ?)
                """, id, userId, id.toString().replace("-", ""), timestamp(expiresAt), timestamp(LONG_AGO));
        return id;
    }

    private String insertThrottle(Instant lastFailureAt) {
        String subjectHash = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                insert into login_throttles (subject_hash, failures, lockouts, locked_until, last_failure_at)
                values (?, 1, 0, null, ?)
                """, subjectHash, timestamp(lastFailureAt));
        return subjectHash;
    }

    private boolean exists(String countQuery, Object key) {
        return jdbc.queryForObject(countQuery, Integer.class, key) > 0;
    }

    private static Timestamp timestamp(Instant instant) {
        return isNull(instant) ? null : Timestamp.from(instant);
    }
}
