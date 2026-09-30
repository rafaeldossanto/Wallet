package com.wallet.core.shared.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void domainExceptionKeepsItsCodeAndMapsItsType() throws Exception {
        mockMvc.perform(get("/fake/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("thing.not_found"))
                .andExpect(jsonPath("$.fields").doesNotExist());

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

    @RestController
    @RequestMapping("/fake")
    public static class FakeController {

        @GetMapping("/not-found")
        public String notFound() {
            throw new DomainException(ErrorType.NOT_FOUND, "thing.not_found", "No thing with this id");
        }

        @GetMapping("/too-soon")
        public String tooSoon() {
            throw new DomainException(ErrorType.TOO_MANY_REQUESTS, "sync.too_soon", "Synced a minute ago");
        }

        @GetMapping("/boom")
        public String boom() {
            throw new IllegalStateException("database password is hunter2");
        }

        @PostMapping("/items")
        public String create(@Valid @RequestBody FakeRequest request) {
            return request.name();
        }
    }

    public record FakeRequest(@NotBlank String name) {
    }
}
