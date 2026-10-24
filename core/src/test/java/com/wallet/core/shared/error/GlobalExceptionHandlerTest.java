package com.wallet.core.shared.error;

import com.wallet.core.shared.web.RequestBodyTooLargeException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.UncheckedIOException;
import java.sql.SQLTransientConnectionException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void aLockedAccountSaysUntilWhen() throws Exception {
        mockMvc.perform(get("/fake/locked"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("auth.locked"))
                .andExpect(jsonPath("$.lockedUntil").value("2026-10-19T20:15:00Z"));
    }

    @Test
    void domainExceptionKeepsItsCodeAndMapsItsType() throws Exception {
        mockMvc.perform(get("/fake/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("thing.not_found"))
                .andExpect(jsonPath("$.fields").doesNotExist())
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());

        mockMvc.perform(get("/fake/too-soon"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("sync.too_soon"));
    }

    @Test
    void invalidBodyListsTheFields() throws Exception {
        mockMvc.perform(post("/fake/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fields[0].field").value("name"));
    }

    @Test
    void unreadableBodyIsMalformedRequest() throws Exception {
        mockMvc.perform(post("/fake/items").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.malformed"));
    }

    @Test
    void unexpectedErrorDoesNotLeakDetails() throws Exception {
        mockMvc.perform(get("/fake/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal.error"))
                .andExpect(jsonPath("$.message").value("Unexpected error"));
    }

    /**
     * The driver's message can carry the user's data ({@code Key (email)=(...) already exists}), and so
     * can a parser's quote of the body: the log keeps the classes and the stack, never the messages,
     * the causes' included (L12).
     */
    @Test
    void anUnexpectedErrorIsLoggedWithoutAnyMessage(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/fake/leaky").header("X-Trace-Id", "ab12cd34"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal.error"));

        assertThat(output.getAll())
                .contains("[EXCEPTION] Unexpected error, trace ab12cd34")
                .contains("java.lang.IllegalStateException")
                .contains("Caused by: org.springframework.dao.DataIntegrityViolationException")
                .contains("GlobalExceptionHandlerTest$FakeController.leaky")
                .doesNotContain("rafael@example.com")
                .doesNotContain("already exists");
    }

    @Test
    void aTraceIdThatIsNotOursIsNotLogged(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/fake/boom").header("X-Trace-Id", "rafael@example.com"))
                .andExpect(status().isInternalServerError());

        assertThat(output.getAll()).contains("[EXCEPTION] Unexpected error, trace -").doesNotContain("rafael@example.com");
    }

    @Test
    void aBodyCutAtTheSizeLimitIsTooLargeNotMalformed() throws Exception {
        mockMvc.perform(get("/fake/too-large"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("request.too_large"));

        mockMvc.perform(get("/fake/too-large-while-parsing"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("request.too_large"));
    }

    @Test
    void aFullBulkheadSaysWhenToTryAgain() throws Exception {
        mockMvc.perform(get("/fake/user-share-taken"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(jsonPath("$.code").value("rate_limit.exceeded"));

        mockMvc.perform(get("/fake/all-slots-taken"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(jsonPath("$.code").value("service.busy"));
    }

    /** What Spring throws when Hikari's connection-timeout runs out: a busy core, not a broken one. */
    @Test
    void noConnectionFreeInThePoolIsBusyNotAnError() throws Exception {
        mockMvc.perform(get("/fake/pool-exhausted"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(jsonPath("$.code").value("service.busy"));

        mockMvc.perform(get("/fake/pool-exhausted-in-jdbc"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("service.busy"));
    }

    @Test
    void anyOtherDatabaseErrorStaysUnexpected() throws Exception {
        mockMvc.perform(get("/fake/constraint"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("internal.error"));
    }

    @RestController
    @RequestMapping("/fake")
    public static class FakeController {

        @GetMapping("/user-share-taken")
        public String userShareTaken() {
            throw ServiceBusyException.userShareTaken("search");
        }

        @GetMapping("/all-slots-taken")
        public String allSlotsTaken() {
            throw ServiceBusyException.full("search");
        }

        @GetMapping("/pool-exhausted")
        public String poolExhausted() {
            throw new CannotCreateTransactionException("Could not open JPA EntityManager for transaction",
                    new IllegalStateException(new SQLTransientConnectionException(
                            "HikariPool-1 - Connection is not available, request timed out after 3000ms")));
        }

        @GetMapping("/pool-exhausted-in-jdbc")
        public String poolExhaustedInJdbc() {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection",
                    new SQLTransientConnectionException("HikariPool-1 - Connection is not available"));
        }

        @GetMapping("/constraint")
        public String constraint() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        }

        @GetMapping("/not-found")
        public String notFound() {
            throw new DomainException(ErrorType.NOT_FOUND, "thing.not_found", "No thing with this id");
        }

        @GetMapping("/too-soon")
        public String tooSoon() {
            throw new DomainException(ErrorType.TOO_MANY_REQUESTS, "sync.too_soon", "Synced a minute ago");
        }

        @GetMapping("/locked")
        public String locked() {
            throw new AccountLockedException(Instant.parse("2026-10-19T20:15:00Z"));
        }

        @GetMapping("/leaky")
        public String leaky() {
            throw new IllegalStateException("could not save rafael@example.com",
                    new DataIntegrityViolationException("ERROR: Key (email)=(rafael@example.com) already exists"));
        }

        @GetMapping("/boom")
        public String boom() {
            throw new IllegalStateException("database password is hunter2");
        }

        @GetMapping("/too-large")
        public String tooLarge() throws RequestBodyTooLargeException {
            throw new RequestBodyTooLargeException(16);
        }

        /** How the JSON reader reports it: the size limit's exception somewhere down the causes. */
        @GetMapping("/too-large-while-parsing")
        public String tooLargeWhileParsing() {
            throw new HttpMessageNotReadableException("I/O error while reading input message",
                    new UncheckedIOException(new RequestBodyTooLargeException(16)), new MockHttpInputMessage(new byte[0]));
        }

        @PostMapping("/items")
        public String create(@Valid @RequestBody FakeRequest request) {
            return request.name();
        }
    }

    public record FakeRequest(@NotBlank String name) {
    }
}
