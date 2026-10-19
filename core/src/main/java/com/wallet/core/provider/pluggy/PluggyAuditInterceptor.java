package com.wallet.core.provider.pluggy;

import com.wallet.core.provider.ProviderCallContext;
import com.wallet.core.provider.ProviderErrors;
import com.wallet.core.provider.entity.ProviderCall;
import com.wallet.core.provider.mapper.ProviderCallMapper;
import com.wallet.core.provider.service.ProviderCallRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.nonNull;

/**
 * Records every HTTP call to Pluggy in the audit trail, {@code POST /auth} included, without
 * touching the methods of {@link PluggyClient}: a new endpoint is recorded from its first call.
 * Who and why come from the caller's {@link ProviderCallContext}.
 *
 * <p>The record is written when RestClient closes the response, so the duration includes reading
 * the body. A call that got no response at all is recorded right away. Neither the request nor the
 * response body is ever looked at: they carry the credentials, the apiKey and the financial data.
 */
@Component
@RequiredArgsConstructor
class PluggyAuditInterceptor implements ClientHttpRequestInterceptor {

    static final String PROVIDER = "PLUGGY";
    /** Error codes for what {@link ProviderErrors} does not tell apart, used only in the audit trail. */
    static final String RATE_LIMITED = "provider.rate_limited";
    static final String TIMEOUT = "provider.timeout";
    static final String UNREACHABLE = "provider.unreachable";
    static final String CONNECTION_FAILED = "provider.connection_failed";

    private final ProviderCallRecorder recorder;
    private final Clock clock;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Call call = new Call(PluggyOperations.of(request.getMethod(), request.getURI()),
                ProviderCallContext.current().orElse(null), clock.instant(), System.nanoTime());
        ClientHttpResponse response = null;
        try {
            response = execution.execute(request, body);
            int status = response.getStatusCode().value();
            return new RecordedResponse(response, () -> recorder.record(call.finish(status, errorCodeOf(status))));
        } catch (IOException | RuntimeException ex) {
            if (nonNull(response)) {
                response.close();
            }
            recorder.record(call.finish(null, errorCodeOf(ex)));
            throw ex;
        }
    }

    /** Same classes of failure {@link PluggyClient} reports, plus rate limiting. */
    static String errorCodeOf(int status) {
        if (status < 400) {
            return null;
        }
        return switch (status) {
            case 401, 403 -> ProviderErrors.AUTH_FAILED;
            case 404 -> ProviderErrors.NOT_FOUND;
            case 429 -> RATE_LIMITED;
            default -> ProviderErrors.UNAVAILABLE;
        };
    }

    /**
     * Could not connect (refused, unknown host, connect timeout: what a blocked egress looks like),
     * connected but no answer in time, or the connection dropped mid-call. Anything else the HTTP
     * client throws counts as the last.
     */
    static String errorCodeOf(Exception ex) {
        if (ex instanceof HttpConnectTimeoutException || ex instanceof ConnectException) {
            return UNREACHABLE;
        }
        if (ex instanceof HttpTimeoutException) {
            return TIMEOUT;
        }
        return CONNECTION_FAILED;
    }

    private record Call(String operation, ProviderCallContext context, Instant calledAt, long startedNanos) {

        ProviderCall finish(Integer httpStatus, String errorCode) {
            long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            return ProviderCallMapper.toEntity(PROVIDER, operation, context, calledAt, durationMs, httpStatus, errorCode);
        }
    }

    /** Records the call once, when RestClient closes the response after reading it. */
    private static final class RecordedResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final Runnable record;
        private final AtomicBoolean recorded = new AtomicBoolean();

        private RecordedResponse(ClientHttpResponse delegate, Runnable record) {
            this.delegate = delegate;
            this.record = record;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() throws IOException {
            return delegate.getBody();
        }

        @Override
        public void close() {
            try {
                delegate.close();
            } finally {
                if (recorded.compareAndSet(false, true)) {
                    record.run();
                }
            }
        }
    }
}
