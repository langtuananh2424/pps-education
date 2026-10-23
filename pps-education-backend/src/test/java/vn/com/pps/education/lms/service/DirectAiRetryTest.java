package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DirectAiRetryTest {

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> status(int code) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(code);
        return r;
    }

    @Test
    void retriesTransientStatusThenSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpResponse<String> r = DirectAiRetry.send("t", 3, () -> calls.incrementAndGet() < 3 ? status(429) : status(200), ms -> { });
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void returnsLastTransientResponseAfterMaxAttempts() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpResponse<String> r = DirectAiRetry.send("t", 3, () -> { calls.incrementAndGet(); return status(529); }, ms -> { });
        assertThat(r.statusCode()).isEqualTo(529);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryClientErrors() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpResponse<String> r = DirectAiRetry.send("t", 3, () -> { calls.incrementAndGet(); return status(401); }, ms -> { });
        assertThat(r.statusCode()).isEqualTo(401);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void retriesConnectionErrorButNotTimeout() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpResponse<String> r = DirectAiRetry.send("t", 3, () -> {
            if (calls.incrementAndGet() == 1) {
                throw new IOException("reset");
            }
            return status(200);
        }, ms -> { });
        assertThat(r.statusCode()).isEqualTo(200);

        AtomicInteger timeoutCalls = new AtomicInteger();
        assertThatThrownBy(() -> DirectAiRetry.send("t", 3, () -> {
            timeoutCalls.incrementAndGet();
            throw new HttpTimeoutException("timed out");
        }, ms -> { })).isInstanceOf(HttpTimeoutException.class);
        assertThat(timeoutCalls.get()).isEqualTo(1);
    }
}
