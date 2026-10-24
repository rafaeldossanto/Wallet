package com.wallet.core.identity;

import com.jayway.jsonpath.JsonPath;
import com.wallet.core.RecordingMailTransport;
import com.wallet.core.identity.controller.ClientAddress;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static java.util.Objects.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The identity routes as the BFF calls them, and the account e-mails as their owner reads them. */
final class IdentityClient {

    static final String PASSWORD = "correct horse battery";
    static final String HOME_ADDRESS = "198.51.100.10";

    private final MockMvc mockMvc;
    private final RecordingMailTransport mail;

    IdentityClient(MockMvc mockMvc, RecordingMailTransport mail) {
        this.mockMvc = mockMvc;
        this.mail = mail;
    }

    static String uniqueEmail() {
        return "rafael+" + UUID.randomUUID() + "@example.com";
    }

    /** Signed up and confirmed through the e-mailed link, ready to sign in with {@link #PASSWORD}. */
    String confirmedAccount() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isAccepted());
        confirm(RecordingMailTransport.tokenIn(mail.await(email))).andExpect(status().isOk());
        return email;
    }

    ResultActions register(String email, String password) throws Exception {
        return json(post("/internal/auth/register"),
                "{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"Rafael\"}".formatted(email, password));
    }

    ResultActions login(String email, String password) throws Exception {
        return login(email, password, null, HOME_ADDRESS);
    }

    ResultActions login(String email, String password, String code, String clientAddress) throws Exception {
        String body = isNull(code)
                ? "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)
                : "{\"email\":\"%s\",\"password\":\"%s\",\"code\":\"%s\"}".formatted(email, password, code);
        return json(post("/internal/auth/login").header(ClientAddress.HEADER, clientAddress), body);
    }

    ResultActions confirm(String token) throws Exception {
        return json(post("/internal/auth/email/confirm"), "{\"token\":\"%s\"}".formatted(token));
    }

    ResultActions resend(String email) throws Exception {
        return json(post("/internal/auth/email/resend"), "{\"email\":\"%s\"}".formatted(email));
    }

    ResultActions refresh(String refreshToken) throws Exception {
        return json(post("/internal/auth/refresh"), "{\"refreshToken\":\"%s\"}".formatted(refreshToken));
    }

    ResultActions me(String accessToken) throws Exception {
        return mockMvc.perform(get("/internal/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    String accessTokenOf(String email, String password) throws Exception {
        return accessTokenOf(login(email, password).andExpect(status().isOk()));
    }

    String accessTokenOf(String email) throws Exception {
        return accessTokenOf(login(email, PASSWORD).andExpect(status().isOk()));
    }

    ResultActions json(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    static String accessTokenOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    static String refreshTokenOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.refreshToken");
    }

    ResultActions forgotPassword(String email) throws Exception {
        return json(post("/internal/auth/password/forgot"), "{\"email\":\"%s\"}".formatted(email));
    }

    ResultActions resetPassword(String token, String newPassword) throws Exception {
        return resetPassword(token, newPassword, HOME_ADDRESS);
    }

    ResultActions resetPassword(String token, String newPassword, String clientAddress) throws Exception {
        return json(post("/internal/auth/password/reset").header(ClientAddress.HEADER, clientAddress),
                "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword));
    }

    ResultActions changePassword(String accessToken, String currentPassword, String newPassword, String clientAddress)
            throws Exception {
        return json(put("/internal/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(ClientAddress.HEADER, clientAddress),
                "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(currentPassword, newPassword));
    }
}
