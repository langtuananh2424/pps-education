package vn.com.pps.education.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.lms.domain.ReviewVideoWatchSession;

import java.util.List;

public interface ReviewVideoWatchSessionRepository extends JpaRepository<ReviewVideoWatchSession, Long> {

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07 — toàn bộ các lượt xem ĐÃ HOÀN TẤT
     * (qualified=true, quizCompletedAt khác NULL; 2026-10-16 gồm cả lượt làm sai, vì lượt sai cũng tính
     * vào "hoàn thành"), sắp theo thời điểm nộp quiz tăng dần — dùng dựng popup "Hoàn thành"/"Kết quả" liệt kê câu đã
     * trả lời qua từng lượt (xem ReviewVideoService#getConnectionAnswerHistory).
     */
    List<ReviewVideoWatchSession> findByReviewVideoIdAndStudentIdAndReviewVideoAssignmentIdAndQualifiedTrueAndQuizCompletedAtIsNotNullAndArchivedAtIsNullOrderByQuizCompletedAtAsc(
            Long reviewVideoId, Long studentId, Long reviewVideoAssignmentId);

    /** V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — thêm lọc theo lần giao, mirror ghi chú ReviewVideoProgressRepository. */
    int countByReviewVideoIdAndStudentIdAndReviewVideoAssignmentIdAndQualifiedTrueAndArchivedAtIsNull(Long reviewVideoId, Long studentId, Long reviewVideoAssignmentId);

    /**
     * V83: CONNECTION giờ cần cả xem đạt ngưỡng LẪN đã trả lời đủ câu hỏi CHO ĐÚNG lượt đó mới tính vào
     * viewCount. V129: thêm lọc theo lần giao. V160 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-09-05): đổi điều kiện từ "đã nộp đủ" (quizCompletedAt khác NULL, không phân biệt đúng/sai)
     * sang "đã nộp đủ VÀ đúng 100%" (quizPassed=true) — sai thì không được tính vào viewCount nữa.
     */
    int countByReviewVideoIdAndStudentIdAndReviewVideoAssignmentIdAndQualifiedTrueAndQuizPassedTrueAndArchivedAtIsNull(
            Long reviewVideoId, Long studentId, Long reviewVideoAssignmentId);

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-16 — số lượt CONNECTION đã xem hết VÀ
     * đã nộp xong câu hỏi (đúng 100% hoặc hết lượt thử mà vẫn sai, quizCompletedAt khác NULL), KHÔNG
     * phân biệt đúng/sai. Dùng tính "hoàn thành" (xem ReviewVideoService#recomputeProgress); viewCount
     * vẫn chỉ đếm lượt ĐẠT (quizPassed=true).
     */
    int countByReviewVideoIdAndStudentIdAndReviewVideoAssignmentIdAndQualifiedTrueAndQuizCompletedAtIsNotNullAndArchivedAtIsNull(
            Long reviewVideoId, Long studentId, Long reviewVideoAssignmentId);

    /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — đếm TOÀN BỘ lượt đã có (kể cả chưa qualified) TRƯỚC lượt mới, dùng tính slotIndex theo chu kỳ modulo M. */
    int countByReviewVideoIdAndStudentIdAndArchivedAtIsNull(Long reviewVideoId, Long studentId);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — gate "Xóa video" (xem ReviewVideoService#deleteVideo): đã có học sinh xem thì không cho xóa. */
    boolean existsByReviewVideoId(Long reviewVideoId);

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-17 — toàn bộ lượt xem (kể cả đã lưu trữ
     * khi học sinh "Làm lại từ đầu") của 1 học sinh trong 1 bản giao, theo thứ tự mở lượt; dùng cho
     * lịch sử từng lượt ở trang thống kê.
     */
    List<ReviewVideoWatchSession> findByReviewVideoIdInAndStudentIdAndReviewVideoAssignmentIdOrderByStartedAtAsc(
            List<Long> reviewVideoIds, Long studentId, Long reviewVideoAssignmentId);

    /** Các lượt đang tính (chưa lưu trữ) của 1 học sinh trong 1 bản giao — dùng khi làm lại từ đầu (ReviewVideoService#retakeConnection). */
    List<ReviewVideoWatchSession> findByReviewVideoIdAndStudentIdAndReviewVideoAssignmentIdAndArchivedAtIsNull(
            Long reviewVideoId, Long studentId, Long reviewVideoAssignmentId);
}
