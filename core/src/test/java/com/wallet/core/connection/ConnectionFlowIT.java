package com.wallet.core.connection;

import com.jayway.jsonpath.JsonPath;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.provider.FinancialDataProvider;
import com.wallet.core.provider.ProviderErrors;
import com.wallet.core.provider.ProviderItem;
import com.wallet.core.provider.ProviderItemStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static com.wallet.core.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Connections in the default MEU_PLUGGY mode, with the provider replaced by a mock. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ConnectionFlowIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FinancialDataProvider provider;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userId = TestUsers.register(mockMvc);
    }

    /**
     * Linking starts the first sync in the background. Left running, it would reach the provider
     * mock after Mockito reset it between tests, get null for the item and fail with an error.
     */
    @AfterEach
    void awaitFirstSyncs() throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
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
        throw new AssertionError("The first sync of a connection of user " + userId + " did not finish");
    }

    @Test
    void linksAnItemAndAnnouncesIt() throws Exception {
        String itemId = stubItem(null);

        String body = link(userId, itemId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.institutionName").value("Nubank"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.lastSyncedAt").isEmpty())
                .andReturn().getResponse().getContentAsString();

        UUID connectionId = UUID.fromString(JsonPath.read(body, "$.id"));
        assertThat(events.stream(ConnectionLinked.class))
                .containsExactly(new ConnectionLinked(connectionId, userId));
        mockMvc.perform(get("/internal/connections").with(as(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(connectionId.toString()));
    }

    @Test
    void anItemCanOnlyBeLinkedOnce() throws Exception {
        String itemId = stubItem(null);
        link(userId, itemId).andExpect(status().isCreated());

        link(TestUsers.register(mockMvc), itemId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("connection.already_linked"));
    }

    @Test
    void unknownItemIsReportedAsSuch() throws Exception {
        String itemId = UUID.randomUUID().toString();
        when(provider.findItem(itemId)).thenThrow(ProviderErrors.notFound("item"));

        link(userId, itemId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("connection.item_not_found"));
    }

    @Test
    void providerOutageIsPassedOn() throws Exception {
        String itemId = UUID.randomUUID().toString();
        when(provider.findItem(itemId)).thenThrow(ProviderErrors.unavailable("timeout"));

        link(userId, itemId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("provider.unavailable"));
    }

    @Test
    void rejectsItemIdsThatAreNotIds() throws Exception {
        link(userId, "../items")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
    }

    @Test
    void usersNeverSeeOrTouchEachOthersConnections() throws Exception {
        String body = link(userId, stubItem(null)).andReturn().getResponse().getContentAsString();
        String connectionId = JsonPath.read(body, "$.id");
        UUID stranger = TestUsers.register(mockMvc);

        mockMvc.perform(get("/internal/connections").with(as(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(delete("/internal/connections/" + connectionId).with(as(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("connection.not_found"));
    }

    @Test
    void unlinkingInMeuPluggyModeLeavesTheItemAtTheProvider() throws Exception {
        String body = link(userId, stubItem(null)).andReturn().getResponse().getContentAsString();
        String connectionId = JsonPath.read(body, "$.id");

        mockMvc.perform(delete("/internal/connections/" + connectionId).with(as(userId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/internal/connections").with(as(userId))).andExpect(jsonPath("$").isEmpty());
        verify(provider, never()).deleteItem(anyString());
    }

    @Test
    void connectionsNeedAToken() throws Exception {
        mockMvc.perform(get("/internal/connections")).andExpect(status().isUnauthorized());
    }

    private String stubItem(String clientUserId) {
        String itemId = UUID.randomUUID().toString();
        when(provider.findItem(itemId)).thenReturn(new ProviderItem(itemId, "Nubank", "https://cdn/nubank.svg",
                ProviderItemStatus.READY, Instant.parse("2026-09-29T09:00:00Z"), null, clientUserId));
        return itemId;
    }

    private ResultActions link(UUID user, String itemId) throws Exception {
        return mockMvc.perform(post("/internal/connections").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerItemId\":\"%s\"}".formatted(itemId)));
    }
}
