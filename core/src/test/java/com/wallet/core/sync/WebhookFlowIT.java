package com.wallet.core.sync;

import com.wallet.core.MutableClock;
import com.wallet.core.TestClockConfiguration;
import com.wallet.core.TestMockMvc;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.connection.ConnectionStatus;
import com.wallet.core.connection.entity.Connection;
import com.wallet.core.connection.repository.ConnectionRepository;
import com.wallet.core.provider.FinancialDataProvider;
import com.wallet.core.provider.ProviderItem;
import com.wallet.core.provider.ProviderItemStatus;
import com.wallet.core.shared.web.Bulkhead;
import com.wallet.core.shared.web.Bulkheads;
import com.wallet.core.sync.service.WebhookEventHousekeeping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pluggy's webhook as a hint (finding A21): it is not signed, so the core takes nothing from it but
 * the event id, the event name and the item id, and always reads Pluggy for itself. Every event is
 * recorded once and handled after the commit, off the request thread; the sync it asks for follows
 * the rules of the "Atualizar" button (cooldown, bulkhead, one sync per connection).
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class WebhookFlowIT {

    private static final String PATH = "/internal/webhooks/pluggy";

    @Autowired private WebApplicationContext context;
    @Autowired private MutableClock clock;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ConnectionRepository connections;
    @Autowired private Bulkheads bulkheads;
    @Autowired private WebhookEventHousekeeping housekeeping;

    @MockitoBean
    private FinancialDataProvider provider;

    private MockMvc mockMvc;
    private UUID userId;
    private String itemId;
    private UUID connectionId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = TestMockMvc.of(context);
        clock.set(TestClockConfiguration.START);
        userId = TestUsers.register(mockMvc);
        itemId = UUID.randomUUID().toString();
        connectionId = saveConnection(null);
        when(provider.findItem(itemId)).thenReturn(new ProviderItem(itemId, "Nubank", null, ProviderItemStatus.READY,
                TestClockConfiguration.START, null, null));
    }

    @Test
    void aNewEventAsksForASyncWithTheWebhookTrigger() throws Exception {
        String eventId = eventId();

        webhook("item/updated", eventId, itemId).andExpect(status().isAccepted()).andExpect(content().string(""));

        assertThat(awaitOutcome(eventId)).isEqualTo("SYNC_REQUESTED");
        awaitFinishedRun();
        assertThat(jdbc.queryForObject("select trigger from sync_runs where connection_id = ?", String.class, connectionId))
                .isEqualTo("WEBHOOK");
        verify(provider).findItem(itemId);
        assertThat(jdbc.queryForObject("select provider_item_id from provider_webhook_events where event_id = ?", String.class, eventId))
                .isEqualTo(itemId);
    }

    @Test
    void theEventsOfTransactionsAndOfErrorsAskForOneToo() throws Exception {
        String created = eventId();
        webhook("transactions/created", created, itemId).andExpect(status().isAccepted());
        assertThat(awaitOutcome(created)).isEqualTo("SYNC_REQUESTED");
        awaitFinishedRun();

        // The next one lands inside the cooldown of the first: the hint is recorded and the poll reads later.
        String error = eventId();
        webhook("item/error", error, itemId).andExpect(status().isAccepted());
        assertThat(awaitOutcome(error)).isEqualTo("SKIPPED_COOLDOWN");
    }

    @Test
    void theSameEventTwiceIsRecordedAndHandledOnce() throws Exception {
        String eventId = eventId();
        webhook("item/updated", eventId, itemId).andExpect(status().isAccepted());
        awaitOutcome(eventId);
        awaitFinishedRun();

        webhook("item/updated", eventId, itemId).andExpect(status().isAccepted());
        Thread.sleep(300);

        assertThat(jdbc.queryForObject("select count(*) from provider_webhook_events where event_id = ?", Integer.class, eventId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from sync_runs where connection_id = ?", Integer.class, connectionId))
                .isEqualTo(1);
    }

    @Test
    void insideTheCooldownTheSyncWaitsForThePoll() throws Exception {
        finishedRun(Duration.ofMinutes(1));
        String eventId = eventId();

        webhook("item/updated", eventId, itemId).andExpect(status().isAccepted());

        assertThat(awaitOutcome(eventId)).isEqualTo("SKIPPED_COOLDOWN");
        assertThat(jdbc.queryForObject("select count(*) from sync_runs where connection_id = ?", Integer.class, connectionId))
                .isEqualTo(1);
        verify(provider, never()).findItem(any());
    }

    @Test
    void theBulkheadOfTheManualUpdatesAppliesToo() throws Exception {
        Bulkhead.Permit first = bulkheads.manualSync().enter(userId);
        Bulkhead.Permit second = bulkheads.manualSync().enter(userId);
        try {
            String eventId = eventId();

            webhook("item/updated", eventId, itemId).andExpect(status().isAccepted());

            assertThat(awaitOutcome(eventId)).isEqualTo("SKIPPED_BUSY");
            verify(provider, never()).findItem(any());
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    void aSyncAlreadyRunningIsNotStartedTwice() throws Exception {
        jdbc.update("insert into sync_runs (id, connection_id, trigger, status, started_at) values (?, ?, 'SCHEDULED', 'RUNNING', ?)",
                UUID.randomUUID(), connectionId, Timestamp.from(clock.instant()));
        String eventId = eventId();

        webhook("item/updated", eventId, itemId).andExpect(status().isAccepted());

        assertThat(awaitOutcome(eventId)).isEqualTo("SKIPPED_BUSY");
        verify(provider, never()).findItem(any());
    }

    @Test
    void anItemNobodyLinkedIsRecordedAndNothingElseHappens() throws Exception {
        String eventId = eventId();

        webhook("item/updated", eventId, "some-other-item").andExpect(status().isAccepted());

        assertThat(awaitOutcome(eventId)).isEqualTo("UNKNOWN_ITEM");
        verify(provider, never()).findItem(any());
        assertThat(jdbc.queryForObject("select count(*) from sync_runs where connection_id = ?", Integer.class, connectionId))
                .isZero();
    }

    @Test
    void aDeletedItemPutsTheConnectionInNeedOfAttention() throws Exception {
        String eventId = eventId();

        webhook("item/deleted", eventId, itemId).andExpect(status().isAccepted());

        assertThat(awaitOutcome(eventId)).isEqualTo("ITEM_DELETED");
        assertThat(statusOfConnection()).isEqualTo("NEEDS_ATTENTION");
        verify(provider, never()).findItem(any());
    }

    @Test
    void aConnectionWhoseAuthorizationEndedIsNotSynced() throws Exception {
        jdbc.update("update connections set consent_expires_at = ? where id = ?",
                Timestamp.from(clock.instant().minus(Duration.ofDays(1))), connectionId);
        String eventId = eventId();

        webhook("item/updated", eventId, itemId).andExpect(status().isAccepted());

        assertThat(awaitOutcome(eventId)).isEqualTo("CONSENT_EXPIRED");
        verify(provider, never()).findItem(any());
        assertThat(jdbc.queryForObject("select count(*) from sync_runs where connection_id = ?", Integer.class, connectionId))
                .isZero();
    }

    /** Nothing but the three fields is read: the rest of the body can say anything. */
    @Test
    void theRestOfTheBodyIsNeverUsed() throws Exception {
        String eventId = eventId();
        String body = """
                {"event":"item/updated","eventId":"%s","itemId":"%s","clientUserId":"%s","connectionId":"%s",
                 "status":"NEEDS_ATTENTION","error":{"code":"x","message":"<script>alert(1)</script>"}}
                """.formatted(eventId, itemId, UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted());

        assertThat(awaitOutcome(eventId)).isEqualTo("SYNC_REQUESTED");
        assertThat(statusOfConnection()).isIn("ACTIVE", "SYNCING");
    }

    @Test
    void aBodyWithoutAnEventIdIsRefused() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"event\":\"item/updated\",\"itemId\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select count(*) from provider_webhook_events where provider_item_id = 'x'", Integer.class))
                .isZero();
    }

    @Test
    void theRouteNeedsTheGatewayKeyButNoSession() throws Exception {
        MockMvc anyone = TestMockMvc.withoutGatewayKey(context);

        anyone.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    /** Run the way the scheduler runs it: the task {@code configureTasks} registers, not the Spring proxy. */
    @Test
    void theScheduledCleanUpKeepsThirtyDays() {
        String old = insertEvent(Duration.ofDays(31));
        String recent = insertEvent(Duration.ofDays(29));

        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        housekeeping.configureTasks(registrar);
        assertThat(registrar.getCronTaskList()).hasSize(1);
        registrar.getCronTaskList().getFirst().getRunnable().run();

        assertThat(eventExists(old)).isFalse();
        assertThat(eventExists(recent)).isTrue();
    }

    private ResultActions webhook(String event, String eventId, String item) throws Exception {
        return mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"%s\",\"eventId\":\"%s\",\"itemId\":\"%s\",\"triggeredBy\":\"CLIENT\"}".formatted(event, eventId, item)));
    }

    private static String eventId() {
        return UUID.randomUUID().toString();
    }

    private UUID saveConnection(Instant consentExpiresAt) {
        Instant now = TestClockConfiguration.START;
        return connections.save(Connection.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .provider("PLUGGY")
                .providerItemId(itemId)
                .institutionName("Nubank")
                .status(ConnectionStatus.ACTIVE)
                .consentExpiresAt(consentExpiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build()).getId();
    }

    private void finishedRun(Duration endedAgo) {
        Instant ended = clock.instant().minus(endedAgo);
        jdbc.update("""
                insert into sync_runs (id, connection_id, trigger, status, started_at, finished_at)
                values (?, ?, 'SCHEDULED', 'SUCCEEDED', ?, ?)
                """, UUID.randomUUID(), connectionId, Timestamp.from(ended.minusSeconds(30)), Timestamp.from(ended));
    }

    private String statusOfConnection() {
        return jdbc.queryForObject("select status from connections where id = ?", String.class, connectionId);
    }

    private String insertEvent(Duration receivedAgo) {
        String eventId = eventId();
        jdbc.update("""
                insert into provider_webhook_events (id, event_id, event, provider_item_id, received_at, processed_at, outcome)
                values (?, ?, 'item/updated', 'item', ?, ?, 'SYNC_REQUESTED')
                """, UUID.randomUUID(), eventId, Timestamp.from(clock.instant().minus(receivedAgo)),
                Timestamp.from(clock.instant().minus(receivedAgo)));
        return eventId;
    }

    private boolean eventExists(String eventId) {
        return jdbc.queryForObject("select count(*) from provider_webhook_events where event_id = ?", Integer.class, eventId) > 0;
    }

    /** Handling happens after the commit on another thread: wait for the outcome to be written. */
    private String awaitOutcome(String eventId) throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            String outcome = jdbc.query("select outcome from provider_webhook_events where event_id = ? and processed_at is not null",
                    rows -> rows.next() ? rows.getString(1) : null, eventId);
            if (outcome != null) {
                return outcome;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The webhook event " + eventId + " was not handled");
    }

    private void awaitFinishedRun() throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            Integer finished = jdbc.queryForObject(
                    "select count(*) from sync_runs where connection_id = ? and status <> 'RUNNING'", Integer.class, connectionId);
            if (finished > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The sync of connection " + connectionId + " did not finish");
    }
}
