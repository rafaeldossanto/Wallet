package com.wallet.bff.exception;

import com.wallet.bff.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        MDC.put(TraceContext.MDC_KEY, "ab12cd34");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    /**
     * What a failure carries in its message (a quote of the request, what the core answered) can be
     * the user's data: the log keeps the classes and the stack, never the messages, the causes'
     * included (L12).
     */
    @Test
    void anUnexpectedErrorIsLoggedWithoutAnyMessage(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/fake/leaky"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal.error"))
                .andExpect(jsonPath("$.message").value("Unexpected error"));

        assertThat(output.getAll())
                .contains("[EXCEPTION] Unexpected error, trace ab12cd34")
                .contains("java.lang.IllegalStateException")
                .contains("Caused by: java.io.UncheckedIOException")
                .contains("GlobalExceptionHandlerTest$FakeController.leaky")
                .doesNotContain("rafael@example.com")
                .doesNotContain("could not");
    }

    @RestController
    @RequestMapping("/fake")
    public static class FakeController {

        @GetMapping("/leaky")
        public String leaky() {
            throw new IllegalStateException("could not read the body of rafael@example.com",
                    new java.io.UncheckedIOException("could not parse {\"email\":\"rafael@example.com\"}",
                            new java.io.IOException("rafael@example.com")));
        }
    }
}
