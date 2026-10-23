package vn.com.pps.education.lms.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.com.pps.education.lms.domain.ReflexGradingJob.Lane;

/**
 * Cấu hình hàng đợi chấm AI Clip phản xạ ({@code app.reflex-queue.*}, V222). Số luồng worker cấu hình riêng
 * từng làn để scale/cân bằng tải khi đổi nhà cung cấp AI.
 */
@Component
public class ReflexQueueSettings {

    private final boolean enabled;
    private final int writingConcurrency;
    private final int speakingConcurrency;
    private final long pollIntervalMs;
    private final int maxTries;
    private final int[] writingBackoffSeconds;
    private final int[] speakingBackoffSeconds;
    private final long staleAfterSeconds;

    public ReflexQueueSettings(@Value("${app.reflex-queue.enabled:false}") boolean enabled,
                               @Value("${app.reflex-queue.writing-concurrency:3}") int writingConcurrency,
                               @Value("${app.reflex-queue.speaking-concurrency:2}") int speakingConcurrency,
                               @Value("${app.reflex-queue.poll-interval-ms:1000}") long pollIntervalMs,
                               @Value("${app.reflex-queue.max-tries:5}") int maxTries,
                               @Value("${app.reflex-queue.writing-backoff-seconds:5,10,20,40,60}") int[] writingBackoffSeconds,
                               @Value("${app.reflex-queue.speaking-backoff-seconds:10,30,60,60,60}") int[] speakingBackoffSeconds,
                               @Value("${app.reflex-queue.stale-after-seconds:300}") long staleAfterSeconds) {
        this.enabled = enabled;
        this.writingConcurrency = Math.max(1, writingConcurrency);
        this.speakingConcurrency = Math.max(1, speakingConcurrency);
        this.pollIntervalMs = Math.max(100, pollIntervalMs);
        this.maxTries = Math.max(1, maxTries);
        this.writingBackoffSeconds = writingBackoffSeconds.length == 0 ? new int[]{5} : writingBackoffSeconds;
        this.speakingBackoffSeconds = speakingBackoffSeconds.length == 0 ? new int[]{10} : speakingBackoffSeconds;
        this.staleAfterSeconds = Math.max(30, staleAfterSeconds);
    }

    /** Test/tiện ích: cấu hình mặc định, hàng đợi tắt. */
    public static ReflexQueueSettings disabled() {
        return new ReflexQueueSettings(false, 3, 2, 1000, 5, new int[]{5, 10, 20, 40, 60}, new int[]{10, 30, 60, 60, 60}, 300);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int concurrency(Lane lane) {
        return lane == Lane.WRITING ? writingConcurrency : speakingConcurrency;
    }

    public long pollIntervalMs() {
        return pollIntervalMs;
    }

    public int maxTries() {
        return maxTries;
    }

    public long staleAfterSeconds() {
        return staleAfterSeconds;
    }

    /** Chờ bao nhiêu giây sau lần thử thứ {@code triesSoFar} (1-based); vượt độ dài mảng thì dùng phần tử cuối. */
    public int backoffSeconds(Lane lane, int triesSoFar) {
        int[] steps = lane == Lane.WRITING ? writingBackoffSeconds : speakingBackoffSeconds;
        return steps[Math.min(Math.max(triesSoFar, 1), steps.length) - 1];
    }
}
