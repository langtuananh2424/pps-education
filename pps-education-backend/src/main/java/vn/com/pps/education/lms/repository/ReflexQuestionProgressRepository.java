package vn.com.pps.education.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.lms.domain.ReflexQuestionProgress;

import java.util.List;
import java.util.Optional;

public interface ReflexQuestionProgressRepository extends JpaRepository<ReflexQuestionProgress, Long> {
    Optional<ReflexQuestionProgress> findByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentId(
            Long reviewVideoQuestionId, Long studentId, Long reviewVideoAssignmentId);

    List<ReflexQuestionProgress> findByReviewVideoAssignmentIdAndStudentId(Long reviewVideoAssignmentId, Long studentId);

    /** V145 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — dùng cho báo cáo GV (ReviewVideoReportService), lấy 1 lần cho cả lớp thay vì N truy vấn/học sinh. */
    List<ReflexQuestionProgress> findByReviewVideoAssignmentIdAndStudentIdIn(Long reviewVideoAssignmentId, List<Long> studentIds);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — gate "Xóa video" (xem ReviewVideoService#deleteVideo): câu hỏi REFLEX của video đã có tiến độ làm bài thì không cho xóa. */
    boolean existsByReviewVideoQuestionIdIn(List<Long> reviewVideoQuestionIds);

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-07 — gọi TRƯỚC khi gọi AI chấm nói trong
     * {@code ReflexSequentialGradingService#submitSpokenAnswer}: lượt bị AI từ chối
     * ({@link vn.com.pps.education.exception.ReflexAudioRejectedException} — không đọc được/nói khác bài
     * viết) làm ROLLBACK cả giao dịch chấm, nhưng VẪN phải tính là 1 lượt đã dùng trong giới hạn
     * {@code MAX_STEP_ATTEMPTS} — tránh học sinh né giới hạn bằng cách gửi bản ghi im lặng/rác liên tục
     * mà không tốn lượt nào. {@code REQUIRES_NEW} (mirror lý do ở {@code AiGradingTokenUsageRecorder})
     * để phép cộng này sống sót qua rollback của giao dịch chấm bên ngoài — chỉ tăng đúng field này,
     * không ghi điểm/feedback của lượt bị từ chối.
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("UPDATE ReflexQuestionProgress p SET p.speakingAttemptCount = p.speakingAttemptCount + 1 WHERE p.id = :id")
    void incrementSpeakingAttemptCountInNewTransaction(@Param("id") Long id);
}
