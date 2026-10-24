package com.wallet.core.sync;

import com.wallet.core.MutableClock;
import com.wallet.core.TestClockConfiguration;
import com.wallet.core.TestMockMvc;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.sync.repository.SyncRunRepository;
import com.wallet.core.sync.service.SyncRunHousekeeping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The nightly clean-up of finished syncs, run the way the scheduler runs it, with a clock the test
 * holds. The latest finished sync of every connection always stays: the "Atualizar" cooldown counts
 * from it.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class SyncRunHousekeepingIT {

    @Autowired
    private SyncRunHousekeeping housekeeping;

    @Autowired
    private SyncRunRepository runs;

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
    void theScheduledRunDeletesFinishedSyncsOlderThanNinetyDaysButAlwaysTheLatestOfEachConnection(CapturedOutput output) {
        UUID busy = connection();
        UUID quiet = connection();
        UUID onlyOneOld = connection();
        UUID running = connection();

        UUID busyOldest = run(busy, "SUCCEEDED", 200);
        UUID busyOlder = run(busy, "FAILED", 120);
        UUID busyRecent = run(busy, "SUCCEEDED", 10);
        UUID quietOld = run(quiet, "SUCCEEDED", 300);
        UUID quietLatestAlthoughOld = run(quiet, "FAILED", 100);
        UUID loneRun = run(onlyOneOld, "SUCCEEDED", 500);
        UUID stillRunningLongAgo = run(running, "RUNNING", 200);
        UUID runningConnectionsLastFinished = run(running, "SUCCEEDED", 250);

        runTheScheduledTask();

        assertThat(exists(busyOldest)).isFalse();
        assertThat(exists(busyOlder)).isFalse();
        assertThat(exists(busyRecent)).isTrue();
        assertThat(exists(quietOld)).isFalse();
        assertThat(exists(quietLatestAlthoughOld)).isTrue();
        assertThat(exists(loneRun)).isTrue();
        // A running sync is never touched, and the finished one behind it is the latest finished.
        assertThat(exists(stillRunningLongAgo)).isTrue();
        assertThat(exists(runningConnectionsLastFinished)).isTrue();
        // What the cooldown reads is still there for every connection.
        for (UUID connection : new UUID[] {busy, quiet, onlyOneOld, running}) {
            assertThat(runs.findLatestOver(connection, PageRequest.of(0, 1))).isNotEmpty();
        }
        assertThat(output.getAll()).contains("[RETENTION] Sync deleted 3 runs that ended more than 90 days ago");
    }

    @Test
    void aSyncThatEndedNinetyDaysAgoIsKeptUntilTheDeadlinePassesAndStillTheLatestStays() {
        UUID connection = connection();
        UUID older = run(connection, "SUCCEEDED", 91);
        UUID latest = run(connection, "SUCCEEDED", 89);

        runTheScheduledTask();
        assertThat(exists(older)).isFalse();
        assertThat(exists(latest)).isTrue();

        clock.advance(Duration.ofDays(30));
        runTheScheduledTask();
        // 119 days old now, and still the only one the cooldown has.
        assertThat(exists(latest)).isTrue();
    }

    private void runTheScheduledTask() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        housekeeping.configureTasks(registrar);
        assertThat(registrar.getCronTaskList()).hasSize(1);
        registrar.getCronTaskList().getFirst().getRunnable().run();
    }

    private UUID connection() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(TestClockConfiguration.START);
        jdbc.update("""
                insert into connections (id, user_id, provider, provider_item_id, status, created_at, updated_at)
                values (?, ?, 'PLUGGY', ?, 'NEEDS_ATTENTION', ?, ?)
                """, id, userId, "item-" + UUID.randomUUID(), now, now);
        return id;
    }

    /** A sync that started and ended {@code daysAgo} days before the clock's now (a minute long). */
    private UUID run(UUID connectionId, String status, int daysAgo) {
        UUID id = UUID.randomUUID();
        Instant started = TestClockConfiguration.START.minus(Duration.ofDays(daysAgo));
        boolean running = "RUNNING".equals(status);
        jdbc.update("""
                insert into sync_runs (id, connection_id, trigger, status, started_at, finished_at)
                values (?, ?, 'SCHEDULED', ?, ?, ?)
                """, id, connectionId, status, Timestamp.from(started),
                running ? null : Timestamp.from(started.plusSeconds(60)));
        return id;
    }

    private boolean exists(UUID runId) {
        return jdbc.queryForObject("select count(*) from sync_runs where id = ?", Integer.class, runId) > 0;
    }
}
