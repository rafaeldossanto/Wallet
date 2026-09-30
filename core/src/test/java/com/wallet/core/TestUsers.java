package com.wallet.core;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Creates real users through the API, so rows that reference {@code users} satisfy their foreign key. */
public final class TestUsers {

    private TestUsers() {
    }

    public static UUID register(MockMvc mockMvc) throws Exception {
        String email = "user+" + UUID.randomUUID() + "@example.com";
        String body = mockMvc.perform(post("/internal/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"displayName\":\"Test\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    /** Authenticates a request as this user, the way the resource server would after validating a JWT. */
    public static RequestPostProcessor as(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }
}
