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
     *
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-08 — vá phòng ngừa race condition (chưa
     * có bằng chứng thật đã xảy ra, chỉ là rủi ro lý thuyết phát hiện khi đọc lại code): thêm điều kiện
     * {@code WHERE ... AND speakingAttemptCount < :max} để phép tăng này cũng LÀ cổng chặn — 2 request
     * cùng lúc cho CÙNG 1 dòng không thể cùng "lách" qua kiểm tra Java rồi cùng tăng (TOCTOU), vì UPDATE
     * ở DB tự atomic theo từng dòng. Trả về số dòng bị ảnh hưởng (0 = đã đủ giới hạn, request phải bị từ
     * chối NGAY, không gọi AI) thay vì void. KHÔNG dùng {@code SELECT ... FOR UPDATE} (khóa dòng) ở tầng
     * Service để tránh deadlock: khóa dòng ở giao dịch ngoài sẽ chặn chính UPDATE REQUIRES_NEW này (dùng
     * kết nối DB khác) trong khi giao dịch ngoài lại đang đợi đồng bộ (cùng 1 thread) kết quả của
     * REQUIRES_NEW — tự khóa chéo chính mình.
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("UPDATE ReflexQuestionProgress p SET p.speakingAttemptCount = p.speakingAttemptCount + 1 " +
            "WHERE p.id = :id AND p.speakingAttemptCount < :max")
    int incrementSpeakingAttemptCountInNewTransactionIfBelowLimit(@Param("id") Long id, @Param("max") int max);

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-08 — như
     * {@link #incrementSpeakingAttemptCountInNewTransactionIfBelowLimit}, cho bước VIẾT (mirror trong
     * {@code submitWrittenAnswer}). KHÔNG cần {@code REQUIRES_NEW}: bước viết không có cơ chế "AI từ chối
     * làm rollback nhưng vẫn tính lượt" như bước nói (AI chấm viết luôn trả {@code null} khi lỗi, không
     * throw — xem {@code ReflexWritingGrammarAiGradingService#grade}), nên chạy trong CHÍNH giao dịch của
     * {@code submitWrittenAnswer} là đủ — lỗi bất thường khác (nếu có) rollback cả lượt tăng này cũng
     * đúng ý (không có gì để "giữ lại" khi bản thân request đó thất bại ngoài dự kiến).
     */
    @Modifying
    @Query("UPDATE ReflexQuestionProgress p SET p.writingAttemptCount = p.writingAttemptCount + 1 " +
            "WHERE p.id = :id AND p.writingAttemptCount < :max")
    int incrementWritingAttemptCountIfBelowLimit(@Param("id") Long id, @Param("max") int max);
}
