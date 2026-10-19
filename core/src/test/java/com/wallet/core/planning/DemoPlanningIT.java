package com.wallet.core.planning;

import com.wallet.core.MutableClock;
import com.wallet.core.TestClockConfiguration;
import com.wallet.core.TestMockMvc;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.connection.ConnectionStatus;
import com.wallet.core.connection.entity.Connection;
import com.wallet.core.connection.repository.ConnectionRepository;
import com.wallet.core.demo.DemoPluggy;
import com.wallet.core.sync.SyncRunStatus;
import com.wallet.core.sync.SyncTrigger;
import com.wallet.core.sync.service.SyncService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.wallet.core.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The demo Pluggy that {@code ./mvnw spring-boot:test-run} serves, read through the real adapter
 * and synced into Postgres: linking {@value DemoPluggy#ITAU_ITEM} gives the planning screen
 * fixed expenses to suggest. If the demo or the rule changes so that it does not, this breaks.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class DemoPlanningIT {

    private static final Instant NOW = Instant.parse("2026-10-18T15:00:00Z");
    private static final DemoPluggy DEMO = startDemo();

    @DynamicPropertySource
    static void pluggyIsTheDemo(DynamicPropertyRegistry registry) {
        registry.add("wallet.pluggy.base-url", DEMO::baseUrl);
        registry.add("wallet.pluggy.client-id", () -> "demo");
        registry.add("wallet.pluggy.client-secret", () -> "demo");
    }

    @AfterAll
    static void stopDemo() {
        DEMO.close();
    }

    @Autowired private WebApplicationContext context;
    @Autowired private MutableClock clock;
    @Autowired private SyncService syncService;
    @Autowired private ConnectionRepository connections;

    /** Itaú's checking account pays the rent and the bills at home every month; the card bill and the salary are not suggested. */
    @Test
    void itausMonthlyDebitsAreSuggested() throws Exception {
        MockMvc mockMvc = TestMockMvc.of(context);
        clock.set(NOW);
        UUID userId = TestUsers.register(mockMvc);
        UUID connectionId = connections.save(Connection.builder().id(UUID.randomUUID()).userId(userId).provider("PLUGGY")
                .providerItemId(DemoPluggy.ITAU_ITEM).institutionName("Itaú").status(ConnectionStatus.ACTIVE)
                .createdAt(NOW).updatedAt(NOW).build()).getId();
        assertThat(syncService.syncNow(connectionId, SyncTrigger.MANUAL)).isEqualTo(SyncRunStatus.SUCCEEDED);

        mockMvc.perform(get("/internal/planning/fixed-expense-suggestions").with(as(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems("Aluguel Apartamento", "Condominio Edificio Sol",
                        "Enel Distribuicao Sp", "Sabesp Agua", "Vivo Fibra", "Plano de Saude")))
                .andExpect(jsonPath("$[*].name", everyItem(not(startsWith("Pagamento Fatura")))))
                .andExpect(jsonPath("$[*].name", everyItem(not(startsWith("Salario")))))
                .andExpect(jsonPath("$[?(@.name == 'Condominio Edificio Sol')].amount", hasItems("690.00")))
                .andExpect(jsonPath("$[?(@.name == 'Condominio Edificio Sol')].dayOfMonth", hasItems(9)));
    }

    private static DemoPluggy startDemo() {
        try {
            return DemoPluggy.start(Clock.fixed(NOW, ZoneOffset.UTC));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
