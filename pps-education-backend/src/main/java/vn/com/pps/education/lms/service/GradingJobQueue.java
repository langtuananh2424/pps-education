package vn.com.pps.education.lms.service;

import vn.com.pps.education.lms.domain.ReflexGradingJob;
import vn.com.pps.education.lms.domain.ReflexGradingJob.Lane;
import vn.com.pps.education.lms.domain.ReflexGradingJob.Status;

import java.util.Optional;

/**
 * Cổng vào hàng đợi chấm AI — nghiệp vụ chỉ biết interface này, phần cài đặt hiện là bảng PostgreSQL
 * ({@link ReflexGradingQueueService}); sau này đổi sang Redis/SQS chỉ thay phần cài đặt.
 */
public interface GradingJobQueue {

    /** Xếp job mới; chạy chung giao dịch của caller (job và trạng thái PENDING cùng commit). */
    ReflexGradingJob enqueue(Long progressId, Lane lane, int attemptNumber, Boolean recordingFilter);

    Optional<ReflexGradingJob> find(Long jobId);

    /** Nhận 1 job đến hạn của làn (đánh dấu RUNNING, tăng tryCount); giao dịch riêng, ngắn. */
    Optional<ReflexGradingJob> claimNext(Lane lane, String workerId);

    /** Đóng job (DONE/REJECTED/FAILED). */
    void close(Long jobId, Status status, String note);

    /** Hẹn thử lại: job về QUEUED, {@code nextRunAt} = bây giờ + khoảng chờ theo số lần đã thử. */
    void scheduleRetry(Long jobId, String error);

    /** Đưa job RUNNING quá hạn về QUEUED; trả số job được cứu. */
    int requeueStale();
}
