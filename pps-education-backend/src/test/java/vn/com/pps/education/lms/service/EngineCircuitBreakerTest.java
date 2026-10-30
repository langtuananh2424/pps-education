package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class EngineCircuitBreakerTest {

    private final AtomicLong now = new AtomicLong(1_000);

    private EngineCircuitBreaker breaker(int threshold) {
        return new EngineCircuitBreaker(threshold, 60_000, now::get);
    }

    @Test
    void staysClosedBelowThresholdAndSuccessResetsTheCount() {
        EngineCircuitBreaker b = breaker(3);
        b.onFailure();
        b.onFailure();
        assertThat(b.tryAcquire()).isTrue();
        b.onSuccess();
        b.onFailure();
        b.onFailure();
        assertThat(b.tryAcquire()).as("lỗi không còn liên tiếp").isTrue();
        assertThat(b.isOpen()).isFalse();
    }

    @Test
    void opensAfterConsecutiveFailuresAndRejectsUntilCooldownEnds() {
        EngineCircuitBreaker b = breaker(3);
        b.onFailure();
        b.onFailure();
        b.onFailure();
        assertThat(b.isOpen()).isTrue();
        assertThat(b.tryAcquire()).isFalse();
        now.addAndGet(59_999);
        assertThat(b.tryAcquire()).isFalse();
        assertThat(b.remainingMillis()).isEqualTo(1);
    }

    @Test
    void afterCooldownExactlyOneProbeGoesThroughAndSuccessClosesTheCircuit() {
        EngineCircuitBreaker b = breaker(2);
        b.onFailure();
        b.onFailure();
        now.addAndGet(60_000);
        assertThat(b.tryAcquire()).as("lời gọi thăm dò").isTrue();
        assertThat(b.tryAcquire()).as("chỉ một lời gọi thăm dò").isFalse();
        b.onSuccess();
        assertThat(b.isOpen()).isFalse();
        assertThat(b.tryAcquire()).isTrue();
        assertThat(b.tryAcquire()).isTrue();
    }

    @Test
    void failedProbeReopensForAnotherCooldown() {
        EngineCircuitBreaker b = breaker(2);
        b.onFailure();
        b.onFailure();
        now.addAndGet(60_000);
        assertThat(b.tryAcquire()).isTrue();
        b.onFailure();
        assertThat(b.tryAcquire()).isFalse();
        now.addAndGet(60_000);
        assertThat(b.tryAcquire()).as("lại cho thăm dò sau chu kỳ mới").isTrue();
    }

    @Test
    void abortedProbeHandsTheProbeBackWithoutCountingAsFailure() {
        EngineCircuitBreaker b = breaker(2);
        b.onFailure();
        b.onFailure();
        now.addAndGet(60_000);
        assertThat(b.tryAcquire()).isTrue();
        b.onAbort();
        assertThat(b.tryAcquire()).isTrue();
    }

    @Test
    void thresholdZeroDisablesTheBreaker() {
        EngineCircuitBreaker b = breaker(0);
        for (int i = 0; i < 10; i++) {
            b.onFailure();
        }
        assertThat(b.tryAcquire()).isTrue();
        assertThat(b.isOpen()).isFalse();
    }
}
