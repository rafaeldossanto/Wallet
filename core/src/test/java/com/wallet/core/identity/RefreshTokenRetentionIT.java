package com.wallet.core.identity;

import com.wallet.core.MutableClock;
import com.wallet.core.TestClockConfiguration;
import com.wallet.core.TestMockMvc;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.identity.service.AccountHousekeeping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refresh tokens that ended long ago, deleted by the nightly clean-up run the way the scheduler runs
 * it, with a clock the test holds. A token that still opens a session, or ended recently, stays.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class RefreshTokenRetentionIT {

    @Autowired
    private AccountHousekeeping housekeeping;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private WebApplicationContext context;

    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(TestClockConfiguration.START);
        userId = TestUsers.register(TestMockMvc.of(context));
    }

    @Test
    void theScheduledRunDeletesTokensExpiredOrRevokedMoreThanThirtyDaysAgo(CapturedOutput output) {
        UUID expiredLongAgo = token(daysFromNow(-31), null, null);
        UUID expiredRecently = token(daysFromNow(-29), null, null);
        UUID revokedLongAgo = token(daysFromNow(10), null, daysFromNow(-31));
        UUID revokedRecently = token(daysFromNow(10), null, daysFromNow(-29));
        UUID rotatedAndExpiredLongAgo = token(daysFromNow(-45), daysFromNow(-75), null);
        UUID rotatedButStillValid = token(daysFromNow(20), daysFromNow(-1), null);
        UUID current = token(daysFromNow(30), null, null);

        runTheScheduledTask();

        assertThat(exists(expiredLongAgo)).isFalse();
        assertThat(exists(revokedLongAgo)).isFalse();
        assertThat(exists(rotatedAndExpiredLongAgo)).isFalse();
        assertThat(exists(expiredRecently)).isTrue();
        assertThat(exists(revokedRecently)).isTrue();
        // Reuse detection still needs a rotated token for as long as it could be presented.
        assertThat(exists(rotatedButStillValid)).isTrue();
        assertThat(exists(current)).isTrue();
        assertThat(output.getAll()).contains("[RETENTION] Identity deleted 3 refresh tokens that ended more than 30 days ago");
    }

    @Test
    void aTokenGoesOnceItsEndTurnsThirtyDaysOld() {
        UUID token = token(daysFromNow(-29), null, null);

        runTheScheduledTask();
        assertThat(exists(token)).isTrue();

        clock.advance(Duration.ofDays(2));
        runTheScheduledTask();
        assertThat(exists(token)).isFalse();
    }

    private void runTheScheduledTask() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        housekeeping.configureTasks(registrar);
        assertThat(registrar.getCronTaskList()).hasSize(1);
        registrar.getCronTaskList().getFirst().getRunnable().run();
    }

    private static Instant daysFromNow(int days) {
        return TestClockConfiguration.START.plus(Duration.ofDays(days));
    }

    private UUID token(Instant expiresAt, Instant rotatedAt, Instant revokedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into refresh_tokens (id, user_id, token_hash, family_id, expires_at, rotated_at, revoked_at, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, userId, id.toString().replace("-", "") + id.toString().replace("-", ""), UUID.randomUUID(),
                Timestamp.from(expiresAt), timestamp(rotatedAt), timestamp(revokedAt),
                Timestamp.from(TestClockConfiguration.START.minus(Duration.ofDays(100))));
        return id;
    }

    private boolean exists(UUID tokenId) {
        return jdbc.queryForObject("select count(*) from refresh_tokens where id = ?", Integer.class, tokenId) > 0;
    }

    private static Timestamp timestamp(Instant instant) {
        return isNull(instant) ? null : Timestamp.from(instant);
    }
}
