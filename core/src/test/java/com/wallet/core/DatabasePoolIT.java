package com.wallet.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;

import static com.wallet.core.TestUsers.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every database connection taken: the core says it is busy, with a time to try again, instead of
 * a 500 after Hikari's default 30 s. A pool of two with a short wait stands in for a full one.
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=250"})
@Import(TestcontainersConfiguration.class)
class DatabasePoolIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DataSource dataSource;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = TestMockMvc.of(context);
        userId = TestUsers.register(mockMvc);
    }

    @Test
    void aFullPoolIsBusyAndTheLivenessProbeDoesNotCare() throws Exception {
        try (Connection first = dataSource.getConnection(); Connection second = dataSource.getConnection()) {
            mockMvc.perform(get("/internal/me").with(as(userId)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                    .andExpect(jsonPath("$.code").value("service.busy"));
            mockMvc.perform(get("/livez"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        mockMvc.perform(get("/internal/me").with(as(userId)))
                .andExpect(status().isOk());
    }
}
