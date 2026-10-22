package com.wallet.bff;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The monthly screens check {@code month} themselves (finding A13): each distinct month is a cache
 * entry and a round of calls to the core, so a month the core would refuse, or one where nothing
 * can exist, never gets that far.
 */
@SpringBootTest
class ScreenMonthRoutesTest extends BffTestSupport {

    private static final List<String> MONTHLY_SCREENS = List.of("/api/calendar", "/api/insights", "/api/planning");

    @Test
    void aMonthThatIsNotYyyyMmNeverReachesTheCore() throws Exception {
        String bearer = bearer(UUID.randomUUID());
        for (String screen : MONTHLY_SCREENS) {
            for (String month : List.of("outubro", "2026-1", "2026-13", "2026-10-01", "10-2026")) {
                mockMvc.perform(get(screen).queryParam("month", month).header(HttpHeaders.AUTHORIZATION, bearer))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("request.invalid_parameter"));
            }
        }

        CORE.verify(0, anyRequestedFor(urlPathMatching("/internal/(insights|planning|connections).*")));
    }

    @Test
    void aMonthOutsideTheWalletsWindowNeverReachesTheCore() throws Exception {
        String bearer = bearer(UUID.randomUUID());
        YearMonth current = YearMonth.now(ZoneId.of("America/Sao_Paulo"));
        for (String screen : MONTHLY_SCREENS) {
            for (YearMonth month : List.of(current.minusMonths(121), current.plusMonths(13), YearMonth.of(1900, 1))) {
                mockMvc.perform(get(screen).queryParam("month", month.toString()).header(HttpHeaders.AUTHORIZATION, bearer))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("request.invalid_parameter"));
            }
        }

        CORE.verify(0, anyRequestedFor(urlPathMatching("/internal/(insights|planning|connections).*")));
    }
}
