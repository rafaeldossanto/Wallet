package com.wallet.core.connection;

import com.wallet.core.TestMockMvc;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.provider.FinancialDataProvider;
import com.wallet.core.provider.ProviderItem;
import com.wallet.core.provider.ProviderItemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Linking waits on the provider without a database connection. With the provider call inside the
 * link's transaction, a user linking ten banks at once (the BFF allows ten an hour) held the whole
 * pool while Pluggy answered: one link at the provider, the others parked on the user's link lock,
 * and everybody else got 503 service.busy. A provider slower than the pool's wait stands in for a
 * slow Pluggy; the pool has its production size.
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=10",
        "spring.datasource.hikari.connection-timeout=1000"})
@Import(TestcontainersConfiguration.class)
class ConnectionLinkPoolIT {

    private static final int LINKS = 10;
    private static final Duration SLOW_PROVIDER = Duration.ofSeconds(2);
    private static final Duration PARKING_TIME = Duration.ofMillis(300);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FinancialDataProvider provider;

    @Test
    void linksWaitingOnASlowProviderLeaveThePoolToEveryoneElse() throws Exception {
        MockMvc mockMvc = TestMockMvc.of(context);
        UUID linking = TestUsers.register(mockMvc);
        UUID reading = TestUsers.register(mockMvc);
        CountDownLatch firstAtTheProvider = new CountDownLatch(1);
        when(provider.findItem(anyString())).thenAnswer(invocation -> {
            firstAtTheProvider.countDown();
            Thread.sleep(SLOW_PROVIDER);
            return item(invocation.getArgument(0));
        });

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> links = new ArrayList<>();
            for (int index = 0; index < LINKS; index++) {
                links.add(executor.submit(() -> mockMvc.perform(post("/internal/connections").with(as(linking))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"providerItemId\":\"%s\"}".formatted(UUID.randomUUID())))
                        .andReturn().getResponse().getStatus()));
            }

            assertThat(firstAtTheProvider.await(10, TimeUnit.SECONDS)).isTrue();
            // Time for the other nine to get as far as they can: at the provider, or parked on a lock.
            Thread.sleep(PARKING_TIME);
            mockMvc.perform(get("/internal/connections").with(as(reading)))
                    .andExpect(status().isOk());

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> link : links) {
                statuses.add(link.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsOnly(201).hasSize(LINKS);
        } finally {
            awaitFirstSyncs(linking);
        }
    }

    private static ProviderItem item(String itemId) {
        return new ProviderItem(itemId, "Nubank", "https://cdn/nubank.svg", ProviderItemStatus.READY,
                Instant.parse("2026-09-29T09:00:00Z"), null, null);
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
