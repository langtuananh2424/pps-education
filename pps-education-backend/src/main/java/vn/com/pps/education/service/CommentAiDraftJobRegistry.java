package vn.com.pps.education.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.com.pps.education.dto.CommentAiDraftJobResponse;
import vn.com.pps.education.dto.CommentAiDraftResult;
import vn.com.pps.education.exception.CommentAiDraftFailedException;
import vn.com.pps.education.exception.CommentAiDraftRejectedException;
import vn.com.pps.education.exception.ResourceNotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 * UC-74 bước 2 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-28) — chạy nền các lượt soạn
 * nháp nhận xét bằng AI và giữ kết quả TRONG BỘ NHỚ cho FE hỏi lại.
 *
 * <p>Vì sao bất đồng bộ: 1 lượt gồm STT + tách ý + viết theo lô + viết lại dòng trùng — tổng thời gian có
 * thể vượt timeout mặc định 60s của nginx ({@code deploy/nginx/admin.conf.template}) dù từng lệnh gọi AI
 * vẫn dưới 90s. Vì sao trong bộ nhớ (không bảng DB): bản xem trước không phải dữ liệu nghiệp vụ — Hậu điều
 * kiện UC-74 yêu cầu KHÔNG ghi gì vào DB trước khi giáo viên bấm Lưu nháp; backend chạy 1 instance/stack
 * nên không cần chia sẻ giữa nhiều máy. Restart backend thì mất job đang chạy — giáo viên gửi lại.</p>
 *
 * <p>Số luồng nhỏ (mặc định 2) + hàng đợi có giới hạn: mọi lệnh gọi AI còn đi qua semaphore chung của
 * {@link NineRouterAiClient}, không để trợ lý nhận xét chiếm hết suất của luồng chấm bài học sinh.</p>
 */
@Component
public class CommentAiDraftJobRegistry {

    private static final Logger log = LoggerFactory.getLogger(CommentAiDraftJobRegistry.class);
    private static final int MAX_QUEUED_JOBS = 20;

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor;
    private final Clock clock;
    private final Duration ttl;

    public CommentAiDraftJobRegistry(@Value("${app.ai-comment-draft.worker-threads:2}") int workerThreads,
                                     @Value("${app.ai-comment-draft.job-ttl-minutes:30}") long ttlMinutes,
                                     Clock clock) {
        AtomicInteger threadCounter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(workerThreads, workerThreads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUED_JOBS), runnable -> {
                    Thread thread = new Thread(runnable, "comment-ai-draft-" + threadCounter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        this.clock = clock;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public CommentAiDraftJobResponse submit(Long ownerUserId, Supplier<CommentAiDraftResult> task) {
        Job job = new Job(UUID.randomUUID().toString(), ownerUserId, clock.instant());
        jobs.put(job.id, job);
        try {
            executor.execute(() -> run(job, task));
        } catch (RejectedExecutionException e) {
            jobs.remove(job.id);
            throw new CommentAiDraftRejectedException(
                    "Trợ lý nhận xét đang xử lý quá nhiều yêu cầu — vui lòng thử lại sau ít phút.");
        }
        return job.toResponse();
    }

    /** Chỉ người tạo job xem được — job của người khác trả 404 như không tồn tại. */
    public CommentAiDraftJobResponse get(String jobId, Long ownerUserId) {
        Job job = jobs.get(jobId);
        if (job == null || !job.ownerUserId.equals(ownerUserId) || isExpired(job)) {
            throw new ResourceNotFoundException("Không tìm thấy yêu cầu soạn nhận xét (có thể đã hết hạn) — vui lòng gửi lại.");
        }
        return job.toResponse();
    }

    @Scheduled(fixedDelay = 300_000)
    public void evictExpired() {
        jobs.values().removeIf(this::isExpired);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private boolean isExpired(Job job) {
        return job.createdAt.plus(ttl).isBefore(clock.instant());
    }

    private void run(Job job, Supplier<CommentAiDraftResult> task) {
        try {
            job.complete(task.get());
        } catch (CommentAiDraftFailedException e) {
            job.fail(e.getMessage());
        } catch (RuntimeException e) {
            log.warn("CommentAiDraftJobRegistry: job {} lỗi không mong đợi.", job.id, e);
            job.fail("Trợ lý nhận xét gặp lỗi không mong đợi — vui lòng thử lại.");
        }
    }

    private static final class Job {
        private final String id;
        private final Long ownerUserId;
        private final Instant createdAt;
        private volatile String status = "RUNNING";
        private volatile String errorMessage;
        private volatile CommentAiDraftResult result;

        private Job(String id, Long ownerUserId, Instant createdAt) {
            this.id = id;
            this.ownerUserId = ownerUserId;
            this.createdAt = createdAt;
        }

        private void complete(CommentAiDraftResult value) {
            result = value;
            status = "DONE";
        }

        private void fail(String message) {
            errorMessage = message;
            status = "FAILED";
        }

        private CommentAiDraftJobResponse toResponse() {
            return new CommentAiDraftJobResponse(id, status, errorMessage, result);
        }
    }
}
