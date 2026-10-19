package com.wallet.bff.config;

import com.wallet.bff.exception.RequestBodyTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static java.util.Objects.isNull;

/**
 * A ceiling on request bodies ({@code wallet.http.max-body-size}), the same filter as the core's.
 * A declared {@code Content-Length} above it is refused with 413 before anything reads the body.
 * A body without one (chunked) is counted while it is read, and reading past the limit throws
 * {@link RequestBodyTooLargeException}, which the {@code GlobalExceptionHandler} answers with 413.
 * A body nobody reads is left to Tomcat, which swallows at most the same size
 * ({@code server.tomcat.max-swallow-size}) and then drops the connection.
 *
 * <p>Right after the trace filter, so the 413 still carries its trace id, and before the security
 * chain: rejecting by size costs nothing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    private final long limit;

    public RequestBodyLimitFilter(HttpProperties properties) {
        this.limit = properties.maxBodySize().toBytes();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > limit) {
            response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            // Built from the configured limit only, nothing from the request: nothing to escape.
            response.getWriter().write("{\"code\":\"request.too_large\",\"message\":\"Request body is larger than "
                    + limit + " bytes\"}");
            return;
        }
        chain.doFilter(new LimitedRequest(request, limit), response);
    }

    /** The same request, with a body that stops at the limit. */
    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long limit;
        private ServletInputStream stream;

        private LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (isNull(stream)) {
                stream = new LimitedInputStream(super.getInputStream(), limit);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = isNull(encoding) ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limit;
        private long read;

        private LimitedInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int next = delegate.read();
            if (next != -1) {
                count(1);
            }
            return next;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                count(count);
            }
            return count;
        }

        private void count(int bytes) throws RequestBodyTooLargeException {
            read += bytes;
            if (read > limit) {
                throw new RequestBodyTooLargeException(limit);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
