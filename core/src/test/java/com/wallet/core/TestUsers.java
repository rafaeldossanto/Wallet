package com.wallet.core;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Creates real users through the API, so rows that reference {@code users} satisfy their foreign key. */
public final class TestUsers {

    public static final String PASSWORD = "password123";

    private TestUsers() {
    }

    /** A new account with its e-mail already confirmed, ready to sign in with {@link #PASSWORD}. */
    public static UUID register(MockMvc mockMvc) throws Exception {
        return register(mockMvc, "user+" + UUID.randomUUID() + "@example.com");
    }

    /**
     * Signs up through the API, which answers the same 202 for every e-mail and sends a link, then
     * confirms the e-mail in the database: the link itself is tested in {@code AuthFlowIT}.
     */
    public static UUID register(MockMvc mockMvc, String email) throws Exception {
        mockMvc.perform(post("/internal/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"Test\"}".formatted(email, PASSWORD)))
                .andExpect(status().isAccepted());
        JdbcTemplate jdbc = mockMvc.getDispatcherServlet().getWebApplicationContext().getBean(JdbcTemplate.class);
        return jdbc.queryForObject("UPDATE users SET email_confirmed_at = created_at WHERE email = ? RETURNING id",
                UUID.class, email);
    }

    /** Authenticates a request as this user, the way the resource server would after validating a JWT. */
    public static RequestPostProcessor as(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }
}
