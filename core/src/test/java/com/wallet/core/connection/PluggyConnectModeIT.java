package com.wallet.core.connection;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static com.wallet.core.TestUsers.as;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Production mode: an item must have been created for this user, and unlinking revokes it at Pluggy. */
@SpringBootTest(properties = "wallet.provider.connection-mode=PLUGGY_CONNECT")
@Import(TestcontainersConfiguration.class)
class PluggyConnectModeIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private FinancialDataProvider provider;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userId = TestUsers.register(mockMvc);
    }

    @Test
    void someoneElsesItemLooksLikeAnUnknownOne() throws Exception {
        String itemId = stubItem(UUID.randomUUID().toString());

        link(itemId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("connection.item_not_found"));
    }

    @Test
    void ownItemIsLinkedAndUnlinkingRevokesItAtTheProvider() throws Exception {
        String itemId = stubItem(userId.toString());

        String body = link(itemId).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(delete("/internal/connections/" + JsonPath.read(body, "$.id")).with(as(userId)))
                .andExpect(status().isNoContent());

        verify(provider).deleteItem(itemId);
    }

    private String stubItem(String clientUserId) {
        String itemId = UUID.randomUUID().toString();
        when(provider.findItem(itemId)).thenReturn(new ProviderItem(itemId, "Itaú", null,
                ProviderItemStatus.READY, Instant.parse("2026-09-29T09:00:00Z"), null, clientUserId));
        return itemId;
    }

    private ResultActions link(String itemId) throws Exception {
        return mockMvc.perform(post("/internal/connections").with(as(userId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerItemId\":\"%s\"}".formatted(itemId)));
    }
}
