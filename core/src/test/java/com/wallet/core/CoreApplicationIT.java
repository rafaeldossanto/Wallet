package com.wallet.core;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole application starts against a real Postgres: datasource, Flyway and JPA
 * validation all have to agree for this to pass.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CoreApplicationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostgreSQLContainer postgres;

    @Autowired
    private DataSource dataSource;

    @Test
    void livenessIsUp() throws Exception {
        mockMvc.perform(get("/livez"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /** Sized on purpose (WALLET_DB_POOL_SIZE), and waiting less for a connection than the BFF waits for the core. */
    @Test
    void theConnectionPoolIsSizedExplicitly() {
        assertThat(dataSource).isInstanceOfSatisfying(HikariDataSource.class, pool -> {
            assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
            assertThat(pool.getConnectionTimeout()).isEqualTo(3_000);
        });
    }

    @Test
    void dockerListsTheDatabaseAsTheCoresTestDatabase() {
        assertThat(postgres.getContainerName()).matches("/?wallet-core-test-db-[0-9a-f]{4}");
    }
}
