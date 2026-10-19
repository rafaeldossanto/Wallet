package com.wallet.bff;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Behind a trusted reverse proxy: the client is the address the proxy saw, nothing the client wrote. */
@SpringBootTest(properties = {"wallet.rate-limit.auth-limit=2", "wallet.rate-limit.trusted-proxies=10.0.0.0/8"})
class RateLimitBehindProxyTest extends BffTestSupport {

    private static final String PROXY = "10.0.0.5";

    @Test
    void eachClientBehindTheProxyHasItsOwnBudget() throws Exception {
        RateLimitTest.stubLogin();

        login(PROXY, "203.0.113.20").andExpect(status().isOk());
        login(PROXY, "203.0.113.20").andExpect(status().isOk());
        login(PROXY, "203.0.113.20").andExpect(status().isTooManyRequests());

        login(PROXY, "203.0.113.21").andExpect(status().isOk());
    }

    /** nginx appends the address it saw; whatever the client put on the left is not believed. */
    @Test
    void aClientCannotRotateTheLeftOfTheHeader() throws Exception {
        RateLimitTest.stubLogin();

        login(PROXY, "198.51.100.1, 203.0.113.30").andExpect(status().isOk());
        login(PROXY, "198.51.100.2, 203.0.113.30").andExpect(status().isOk());
        login(PROXY, "198.51.100.3, 203.0.113.30").andExpect(status().isTooManyRequests());
    }

    @Test
    void outsideTheTrustedRangeTheHeaderIsIgnored() throws Exception {
        RateLimitTest.stubLogin();

        login("198.51.100.77", "203.0.113.40").andExpect(status().isOk());
        login("198.51.100.77", "203.0.113.41").andExpect(status().isOk());
        login("198.51.100.77", "203.0.113.42").andExpect(status().isTooManyRequests());
    }

    private ResultActions login(String address, String forwardedFor) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .header("X-Forwarded-For", forwardedFor)
                .header("X-Wallet-Client", "mobile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"rafael@example.com\",\"password\":\"secret-password\"}"));
    }
}
