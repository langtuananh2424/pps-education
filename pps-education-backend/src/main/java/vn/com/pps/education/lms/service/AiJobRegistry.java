package vn.com.pps.education.lms.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.com.pps.education.exception.CommentAiDraftFailedException;
import vn.com.pps.education.exception.CommentAiDraftRejectedException;
import vn.com.pps.education.exception.ResourceNotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Chạy nền các lượt gọi AI của trợ lý (UC-74 soạn nháp nhận xét, UC-75 soát/đề xuất sửa nhận xét chờ duyệt —
 * bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-28/29) và giữ kết quả TRONG BỘ NHỚ cho FE hỏi lại.
 *
 * <p>Vì sao bất đồng bộ: 1 lượt có thể gồm nhiều lệnh gọi AI nối tiếp — tổng thời gian dễ vượt timeout mặc
 * định 60s của nginx ({@code deploy/nginx/admin.conf.template}). Vì sao trong bộ nhớ (không bảng DB): kết quả
 * chỉ là bản xem trước/gợi ý, không phải dữ liệu nghiệp vụ; backend chạy 1 instance/stack. Restart backend
 * thì mất job đang chạy — người dùng gửi lại.</p>
 *
 * <p>Số luồng nhỏ + hàng đợi có giới hạn: mọi lệnh gọi AI còn đi qua semaphore chung của
 * {@link NineRouterAiClient}, không để trợ lý chiếm hết suất của luồng chấm bài học sinh. Bổ sung 2026-10-01 (đã
 * xác nhận với người dùng): tách 2 làn {@link Lane} — giáo viên soạn nháp cuối ca và Quản lý soát duyệt không còn
 * xếp chung 1 hàng đợi (1 lượt soạn nháp giữ luồng vài phút). Mặc định 2 + 1 luồng: tổng 3, vẫn dưới trần 5 lệnh
 * gọi đồng thời của semaphore chung.</p>
 *
 * <p>Kết quả lưu dạng {@code Object}; service gọi tự kiểm kiểu khi đọc ({@link #get}) để job của loại này
 * không đọc được qua endpoint của loại khác.</p>
 */
@Component
public class AiJobRegistry {

    private static final Logger log = LoggerFactory.getLogger(AiJobRegistry.class);
    private static final int MAX_QUEUED_JOBS = 20;

    /** Trạng thái 1 job: {@code RUNNING}, {@code DONE} ({@code result} có dữ liệu) hoặc {@code FAILED}. */
    public record Snapshot<T>(String jobId, String status, String errorMessage, T result) {
    }

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<Lane, ThreadPoolExecutor> executors = new EnumMap<>(Lane.class);
    private final Clock clock;
    private final Duration ttl;

    /** Làn chạy job: {@code DRAFT} — trợ lý soạn nháp của giáo viên (UC-74 hằng ngày, UC-76 Giữa/Cuối kỳ); {@code REVIEW} — trợ lý duyệt của Quản lý (UC-75). */
    public enum Lane {
        DRAFT, REVIEW
    }

    public AiJobRegistry(@Value("${app.ai-comment-draft.worker-threads:2}") int draftWorkerThreads,
                         @Value("${app.ai-comment-draft.review-worker-threads:1}") int reviewWorkerThreads,
                         @Value("${app.ai-comment-draft.job-ttl-minutes:30}") long ttlMinutes,
                         Clock clock) {
        executors.put(Lane.DRAFT, newExecutor("ai-draft-job-", draftWorkerThreads));
        executors.put(Lane.REVIEW, newExecutor("ai-review-job-", reviewWorkerThreads));
        this.clock = clock;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    private static ThreadPoolExecutor newExecutor(String threadPrefix, int threads) {
        int size = Math.max(1, threads);
        AtomicInteger threadCounter = new AtomicInteger();
        return new ThreadPoolExecutor(size, size, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUED_JOBS), runnable -> {
                    Thread thread = new Thread(runnable, threadPrefix + threadCounter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public <T> Snapshot<T> submit(Long ownerUserId, Lane lane, Supplier<T> task) {
        Job job = new Job(UUID.randomUUID().toString(), ownerUserId, clock.instant());
        jobs.put(job.id, job);
        try {
            executors.get(lane).execute(() -> run(job, task));
        } catch (RejectedExecutionException e) {
            jobs.remove(job.id);
            throw new CommentAiDraftRejectedException(
                    "Trợ lý AI đang xử lý quá nhiều yêu cầu — vui lòng thử lại sau ít phút.");
        }
        return job.snapshot(null);
    }

    /**
     * Chỉ người tạo job xem được, và chỉ qua đúng loại kết quả — job của người khác, của loại khác, hoặc đã
     * hết hạn đều trả 404 như không tồn tại.
     */
    public <T> Snapshot<T> get(String jobId, Long ownerUserId, Class<T> resultType) {
        Job job = jobs.get(jobId);
        if (job == null || !job.ownerUserId.equals(ownerUserId) || isExpired(job)
                || (job.result != null && !resultType.isInstance(job.result))) {
            throw new ResourceNotFoundException("Không tìm thấy yêu cầu của trợ lý AI (có thể đã hết hạn) — vui lòng gửi lại.");
        }
        return job.snapshot(resultType);
    }

    @Scheduled(fixedDelay = 300_000)
    public void evictExpired() {
        jobs.values().removeIf(this::isExpired);
    }

    @PreDestroy
    public void shutdown() {
        executors.values().forEach(ThreadPoolExecutor::shutdownNow);
    }

    private boolean isExpired(Job job) {
        return job.createdAt.plus(ttl).isBefore(clock.instant());
    }

    private void run(Job job, Supplier<?> task) {
        try {
            job.complete(task.get());
        } catch (CommentAiDraftFailedException e) {
            job.fail(e.getMessage());
        } catch (RuntimeException e) {
            log.warn("AiJobRegistry: job {} lỗi không mong đợi.", job.id, e);
            job.fail("Trợ lý AI gặp lỗi không mong đợi — vui lòng thử lại.");
        }
    }

    private static final class Job {
        private final String id;
        private final Long ownerUserId;
        private final Instant createdAt;
        private volatile String status = "RUNNING";
        private volatile String errorMessage;
        private volatile Object result;

        private Job(String id, Long ownerUserId, Instant createdAt) {
            this.id = id;
            this.ownerUserId = ownerUserId;
            this.createdAt = createdAt;
        }

        private void complete(Object value) {
            result = value;
            status = "DONE";
        }

        private void fail(String message) {
            errorMessage = message;
            status = "FAILED";
        }

        private <T> Snapshot<T> snapshot(Class<T> type) {
            return new Snapshot<>(id, status, errorMessage, type == null || result == null ? null : type.cast(result));
        }
    }
}
