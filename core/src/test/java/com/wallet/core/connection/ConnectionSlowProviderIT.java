package com.wallet.core.connection;

import com.wallet.core.TestMockMvc;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.provider.FinancialDataProvider;
import com.wallet.core.provider.ProviderItem;
import com.wallet.core.provider.ProviderItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.wallet.core.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Linking and unlinking wait on the provider without a database connection. With the provider
 * call inside the transaction, a user linking or unlinking ten banks at once (the ceiling is ten
 * connections) held the whole pool while Pluggy answered, and everybody else got 503
 * service.busy. A provider slower than the pool's wait stands in for a slow Pluggy; the pool has
 * its production size.
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=10",
        "spring.datasource.hikari.connection-timeout=1000"})
@Import(TestcontainersConfiguration.class)
class ConnectionSlowProviderIT {

    private static final int REQUESTS = 10;
    private static final Duration SLOW_PROVIDER = Duration.ofSeconds(2);
    private static final Duration PARKING_TIME = Duration.ofMillis(300);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FinancialDataProvider provider;

    private MockMvc mockMvc;
    private UUID heavyUser;
    private UUID otherUser;
    private CountDownLatch firstAtTheProvider;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = TestMockMvc.of(context);
        heavyUser = TestUsers.register(mockMvc);
        otherUser = TestUsers.register(mockMvc);
        firstAtTheProvider = new CountDownLatch(1);
        when(provider.findItem(anyString())).thenAnswer(invocation -> {
            firstAtTheProvider.countDown();
            Thread.sleep(SLOW_PROVIDER);
            return item(invocation.getArgument(0), heavyUser);
        });
    }

    @Test
    void linksWaitingOnASlowProviderLeaveThePoolToEveryoneElse() throws Exception {
        List<RequestBuilder> links = new ArrayList<>();
        for (int index = 0; index < REQUESTS; index++) {
            links.add(post("/internal/connections").with(as(heavyUser)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"providerItemId\":\"%s\"}".formatted(UUID.randomUUID())));
        }

        try {
            assertThat(sendAllWhileAnotherUserReads(links)).containsOnly(201).hasSize(REQUESTS);
        } finally {
            awaitFirstSyncs(heavyUser);
        }
    }

    /** In development mode unlinking asks the provider whose item it is, then revokes it there. */
    @Test
    void unlinksWaitingOnASlowProviderLeaveThePoolToEveryoneElse() throws Exception {
        List<RequestBuilder> unlinks = new ArrayList<>();
        for (int index = 0; index < REQUESTS; index++) {
            unlinks.add(delete("/internal/connections/" + insertSyncedConnection(heavyUser)).with(as(heavyUser)));
        }

        assertThat(sendAllWhileAnotherUserReads(unlinks)).containsOnly(204).hasSize(REQUESTS);
        assertThat(jdbc.queryForObject("select count(*) from connections where user_id = ?", Integer.class, heavyUser))
                .isZero();
    }

    /**
     * Sends the heavy user's requests all at once and, while the provider holds them, has another
     * user read their connections: that read must not wait for a database connection.
     */
    private List<Integer> sendAllWhileAnotherUserReads(List<RequestBuilder> requests) throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> responses = new ArrayList<>();
            for (RequestBuilder request : requests) {
                responses.add(executor.submit(() -> mockMvc.perform(request).andReturn().getResponse().getStatus()));
            }

            assertThat(firstAtTheProvider.await(10, TimeUnit.SECONDS)).isTrue();
            // Time for the others to get as far as they can: at the provider, or parked on a lock.
            Thread.sleep(PARKING_TIME);
            mockMvc.perform(get("/internal/connections").with(as(otherUser)))
                    .andExpect(status().isOk());

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> response : responses) {
                statuses.add(response.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        }
    }

    /** Created by the widget for this user, so unlinking revokes it at the provider. */
    private static ProviderItem item(String itemId, UUID owner) {
        return new ProviderItem(itemId, "Nubank", "https://cdn/nubank.svg", ProviderItemStatus.READY,
                Instant.parse("2026-09-29T09:00:00Z"), null, owner.toString());
    }

    /** With a finished sync, so nothing runs in the background against the provider mock. */
    private UUID insertSyncedConnection(UUID owner) {
        Timestamp now = Timestamp.from(Instant.parse("2026-10-01T09:00:00Z"));
        UUID connectionId = UUID.randomUUID();
        jdbc.update("""
                insert into connections (id, user_id, provider, provider_item_id, status, created_at, updated_at)
                values (?, ?, 'PLUGGY', ?, 'ACTIVE', ?, ?)
                """, connectionId, owner, "item-" + UUID.randomUUID(), now, now);
        jdbc.update("""
                insert into sync_runs (id, connection_id, trigger, status, started_at, finished_at)
                values (?, ?, 'INITIAL', 'SUCCEEDED', ?, ?)
                """, UUID.randomUUID(), connectionId, now, now);
        return connectionId;
    }

    /** Each link starts a first sync in the background; left running, it would outlive the provider mock. */
    private void awaitFirstSyncs(UUID userId) throws InterruptedException {
        for (int attempt = 0; attempt < 400; attempt++) {
            Integer pending = jdbc.queryForObject("""
                    select count(*) from connections c
                     where c.user_id = ?
                       and not exists (select 1 from sync_runs r where r.connection_id = c.id and r.status <> 'RUNNING')
                    """, Integer.class, userId);
            if (pending == 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The first syncs of user " + userId + " did not finish");
    }
}
