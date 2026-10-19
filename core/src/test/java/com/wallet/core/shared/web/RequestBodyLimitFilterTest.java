package com.wallet.core.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestBodyLimitFilterTest {

    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(new HttpProperties(DataSize.ofBytes(16)));

    @Test
    void aDeclaredLengthAboveTheLimitIsRefusedBeforeAnythingReadsIt() throws Exception {
        MockHttpServletRequest request = request("x".repeat(17));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> reached.set(true));

        assertThat(reached).isFalse();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"code\":\"request.too_large\"").contains("16 bytes");
    }

    @Test
    void aBodyWithinTheLimitIsReadWhole() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();

        filter.doFilter(request("x".repeat(16)), new MockHttpServletResponse(),
                (req, res) -> body.set(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));

        assertThat(body.get()).hasSize(16);
    }

    /** Chunked: no Content-Length to look at, so the count happens while the body is read. */
    @Test
    void aBodyWithoutLengthIsCutWhileRead() {
        FilterChain readsEverything = (req, res) -> req.getInputStream().readAllBytes();

        assertThatThrownBy(() -> filter.doFilter(chunked("x".repeat(40)), new MockHttpServletResponse(), readsEverything))
                .isInstanceOf(RequestBodyTooLargeException.class)
                .hasMessage("Request body is larger than 16 bytes");
    }

    @Test
    void readingByteByByteIsCountedToo() {
        FilterChain readsByteByByte = (req, res) -> {
            while (req.getInputStream().read() != -1) {
                // keep reading
            }
        };

        assertThatThrownBy(() -> filter.doFilter(chunked("x".repeat(40)), new MockHttpServletResponse(), readsByteByByte))
                .isInstanceOf(RequestBodyTooLargeException.class);
    }

    @Test
    void theReaderIsLimitedToo() {
        FilterChain readsText = (req, res) -> {
            char[] buffer = new char[8];
            while (req.getReader().read(buffer) != -1) {
                // keep reading
            }
        };

        assertThatThrownBy(() -> filter.doFilter(chunked("x".repeat(40)), new MockHttpServletResponse(), readsText))
                .isInstanceOf(RequestBodyTooLargeException.class);
    }

    private static MockHttpServletRequest request(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/auth/login");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    /** What Tomcat hands over for {@code Transfer-Encoding: chunked}: a body, but no length. */
    private static HttpServletRequestWrapper chunked(String body) {
        return new HttpServletRequestWrapper(request(body)) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
    }
}
