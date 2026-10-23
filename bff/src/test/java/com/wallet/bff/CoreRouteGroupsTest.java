package com.wallet.bff;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.wallet.bff.client.CoreCallGuards;
import com.wallet.bff.client.CoreRouteGroup;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Each group of the core's routes fails and fills up on its own (finding A12). */
@SpringBootTest
class CoreRouteGroupsTest extends BffTestSupport {

    private static final String CONNECTION = "7b0c2a3e-8f61-4d2c-9a4b-2f1e3d5c6a7b";
    private static final String PAGE = "{\"items\":[],\"page\":1,\"pageSize\":50,\"total\":0,\"totalPages\":0}";

    @Autowired
    private CoreCallGuards guards;

    /** Before, one circuit for every route: a Pluggy-bound route timing out took the whole app down. */
    @Test
    void aFailingGroupOpensOnlyItsOwnCircuit() throws Exception {
        CORE.stubFor(WireMock.delete(urlEqualTo("/internal/connections/" + CONNECTION))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        CORE.stubFor(WireMock.get(urlEqualTo("/internal/me"))
                .willReturn(okJson("{\"id\":\"%s\",\"email\":\"rafael@example.com\"}".formatted(UUID.randomUUID()))));
        String bearer = bearer(UUID.randomUUID());

        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(delete("/api/connections/" + CONNECTION).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isServiceUnavailable());
        }
        int reachedTheCore = CORE.countRequestsMatching(
                deleteRequestedFor(urlEqualTo("/internal/connections/" + CONNECTION)).build()).getCount();

        assertThat(guards.circuit(CoreRouteGroup.PROVIDER).getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(guards.circuit(CoreRouteGroup.READS).getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/connections/" + CONNECTION).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("bff.core_unavailable"));
        assertThat(CORE.countRequestsMatching(
                deleteRequestedFor(urlEqualTo("/internal/connections/" + CONNECTION)).build()).getCount())
                .isEqualTo(reachedTheCore);
    }

    /** Every search slot in use: searching waits a second, the plain statement does not. */
    @Test
    void aFullGroupIsBusyWhileTheOthersGoOn() throws Exception {
        CORE.stubFor(WireMock.get(urlPathEqualTo("/internal/transactions")).willReturn(okJson(PAGE)));
        String bearer = bearer(UUID.randomUUID());
        Bulkhead search = guards.bulkhead(CoreRouteGroup.SEARCH);
        int slots = search.getMetrics().getMaxAllowedConcurrentCalls();
        for (int slot = 0; slot < slots; slot++) {
            assertThat(search.tryAcquirePermission()).isTrue();
        }
        try {
            mockMvc.perform(get("/api/transactions").queryParam("q", "padaria").header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                    .andExpect(jsonPath("$.code").value("service.busy"));
            mockMvc.perform(get("/api/transactions").header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk());
        } finally {
            for (int slot = 0; slot < slots; slot++) {
                search.releasePermission();
            }
        }

        CORE.verify(1, getRequestedFor(urlPathEqualTo("/internal/transactions")));
        mockMvc.perform(get("/api/transactions").queryParam("q", "padaria").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    /** A full group is the BFF holding back, not the core failing: it never opens the circuit. */
    @Test
    void aFullGroupDoesNotCountAgainstItsCircuit() throws Exception {
        Bulkhead screens = guards.bulkhead(CoreRouteGroup.SCREENS);
        int slots = screens.getMetrics().getMaxAllowedConcurrentCalls();
        for (int slot = 0; slot < slots; slot++) {
            screens.tryAcquirePermission();
        }
        try {
            for (int attempt = 0; attempt < 12; attempt++) {
                mockMvc.perform(get("/api/calendar").header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                        .andExpect(status().isServiceUnavailable());
            }
        } finally {
            for (int slot = 0; slot < slots; slot++) {
                screens.releasePermission();
            }
        }

        assertThat(guards.circuit(CoreRouteGroup.SCREENS).getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(guards.circuit(CoreRouteGroup.SCREENS).getMetrics().getNumberOfBufferedCalls()).isZero();
    }
}
