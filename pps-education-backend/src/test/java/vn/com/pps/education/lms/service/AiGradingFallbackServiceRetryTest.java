package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.com.pps.education.exception.ReflexAudioRejectedException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** withRetries: thử lại kết quả null / ngoại lệ tối đa N lần rồi mới trả null để caller rơi về 9Router. */
class AiGradingFallbackServiceRetryTest {

    private final AiGradingFallbackService service = new AiGradingFallbackService(
            mock(WritingAiGradingService.class), mock(ReflexV2AiGradingService.class), mock(ReflexV2Prompts.class),
            mock(ClaudeAiClient.class), mock(GroqSpeechClient.class), mock(AzurePronunciationClient.class), mock(AudioTranscoder.class));

    AiGradingFallbackServiceRetryTest() {
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
    }

    @Test
    void retriesNullThenReturnsFirstUsableResult() {
        AtomicInteger calls = new AtomicInteger();
        String r = service.withRetries("t", () -> calls.incrementAndGet() < 3 ? null : "ok");
        assertThat(r).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void stopsAfterMaxAttemptsAndReturnsNull() {
        AtomicInteger calls = new AtomicInteger();
        String r = service.withRetries("t", () -> {
            calls.incrementAndGet();
            return null;
        });
        assertThat(r).isNull();
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void runtimeExceptionCountsAsAttemptAndLastOneIsRethrown() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> service.withRetries("t", () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void audioRejectedIsNeverRetried() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> service.withRetries("t", () -> {
            calls.incrementAndGet();
            throw new ReflexAudioRejectedException("x");
        })).isInstanceOf(ReflexAudioRejectedException.class);
        assertThat(calls.get()).isEqualTo(1);
    }
}
