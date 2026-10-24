package com.wallet.core.banking;

import com.wallet.core.MutableClock;
import com.wallet.core.TestClockConfiguration;
import com.wallet.core.TestMockMvc;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.banking.service.BankingHousekeeping;
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

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The nightly clean-up of transactions the provider stopped reporting, run the way the scheduler
 * runs it (the task {@code configureTasks} registers, not the Spring proxy), with a clock the test
 * holds. What the product shows (balances, bills, live transactions) is never touched.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class BankingHousekeepingIT {

    @Autowired
    private BankingHousekeeping housekeeping;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private WebApplicationContext context;

    private UUID userId;
    private UUID accountId;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(TestClockConfiguration.START);
        userId = TestUsers.register(TestMockMvc.of(context));
        UUID connectionId = UUID.randomUUID();
        Timestamp now = Timestamp.from(TestClockConfiguration.START);
        jdbc.update("""
                insert into connections (id, user_id, provider, provider_item_id, status, created_at, updated_at)
                values (?, ?, 'PLUGGY', ?, 'NEEDS_ATTENTION', ?, ?)
                """, connectionId, userId, "item-" + UUID.randomUUID(), now, now);
        accountId = UUID.randomUUID();
        jdbc.update("""
                insert into accounts (id, connection_id, user_id, provider_account_id, kind, currency_code, balance,
                                      created_at, updated_at)
                values (?, ?, ?, ?, 'CHECKING', 'BRL', 100.00, ?, ?)
                """, accountId, connectionId, userId, "acc-" + UUID.randomUUID(), now, now);
    }

    @Test
    void theScheduledRunDeletesWhatTheProviderRemovedMoreThanNinetyDaysAgoAndNothingElse(CapturedOutput output) {
        UUID removedLongAgo = transaction(daysBeforeNow(91));
        UUID removedLongAgoToo = transaction(daysBeforeNow(400));
        UUID removedRecently = transaction(daysBeforeNow(89));
        UUID removedYesterday = transaction(daysBeforeNow(1));
        UUID alive = transaction(null);
        UUID aliveAndOld = transactionBookedOn(LocalDate.of(2024, 1, 5));
        jdbc.update("insert into balance_snapshots (account_id, snapshot_date, balance) values (?, ?, 80.00)",
                accountId, Date.valueOf(LocalDate.of(2024, 1, 5)));
        jdbc.update("""
                insert into credit_card_bills (id, account_id, provider_bill_id, due_date, total_amount, currency_code,
                                               created_at, updated_at)
                values (?, ?, ?, ?, 50.00, 'BRL', ?, ?)
                """, UUID.randomUUID(), accountId, "bill-" + UUID.randomUUID(), Date.valueOf(LocalDate.of(2024, 1, 10)),
                Timestamp.from(TestClockConfiguration.START), Timestamp.from(TestClockConfiguration.START));

        runTheScheduledTask();

        assertThat(exists(removedLongAgo)).isFalse();
        assertThat(exists(removedLongAgoToo)).isFalse();
        assertThat(exists(removedRecently)).isTrue();
        assertThat(exists(removedYesterday)).isTrue();
        assertThat(exists(alive)).isTrue();
        assertThat(exists(aliveAndOld)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from balance_snapshots where account_id = ?", Integer.class, accountId))
                .isOne();
        assertThat(jdbc.queryForObject("select count(*) from credit_card_bills where account_id = ?", Integer.class, accountId))
                .isOne();
        // Counts only: no description, no value, no id of a person.
        assertThat(output.getAll()).contains("[RETENTION] Banking deleted 2 transactions removed by the provider more than 90 days ago");
    }

    /** The deadline moves with the clock: what was kept yesterday goes once it is 90 days old. */
    @Test
    void aTransactionGoesWhenItsRemovalTurnsNinetyDaysOld() {
        UUID removed = transaction(daysBeforeNow(89));

        runTheScheduledTask();
        assertThat(exists(removed)).isTrue();

        clock.advance(Duration.ofDays(2));
        runTheScheduledTask();
        assertThat(exists(removed)).isFalse();
    }

    private void runTheScheduledTask() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        housekeeping.configureTasks(registrar);
        assertThat(registrar.getCronTaskList()).hasSize(1);
        registrar.getCronTaskList().getFirst().getRunnable().run();
    }

    private static Instant daysBeforeNow(int days) {
        return TestClockConfiguration.START.minus(Duration.ofDays(days));
    }

    private UUID transaction(Instant deletedAt) {
        return insert(LocalDate.of(2026, 7, 1), deletedAt);
    }

    private UUID transactionBookedOn(LocalDate day) {
        return insert(day, null);
    }

    private UUID insert(LocalDate bookedOn, Instant deletedAt) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(TestClockConfiguration.START);
        jdbc.update("""
                insert into transactions (id, account_id, user_id, provider_transaction_id, booked_on, amount, direction,
                                          status, deleted_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, 10.00, 'OUTFLOW', 'POSTED', ?, ?, ?)
                """, id, accountId, userId, "tx-" + id, Date.valueOf(bookedOn),
                deletedAt == null ? null : Timestamp.from(deletedAt), now, now);
        return id;
    }

    private boolean exists(UUID transactionId) {
        return jdbc.queryForObject("select count(*) from transactions where id = ?", Integer.class, transactionId) > 0;
    }
}
