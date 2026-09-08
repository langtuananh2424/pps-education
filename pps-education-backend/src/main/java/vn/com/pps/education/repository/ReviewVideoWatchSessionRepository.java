package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoWatchSession;

import java.util.List;

public interface ReviewVideoWatchSessionRepository extends JpaRepository<ReviewVideoWatchSession, Long> {

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07 — toàn bộ các lượt xem ĐÃ ĐẠT
     * (qualified=true, quizPassed=true — đúng điều kiện đang được tính vào viewCount, xem
     * {@link #countByReviewVideoIdAndStudentIdAndReviewVideoAssignmentIdAndQualifiedTrueAndQuizPassedTrue}),
     * sắp theo thời điểm nộp quiz tăng dần — dùng dựng popup "Hoàn thành"/"Kết quả" liệt kê câu đã
     * trả lời qua từng lượt (xem ReviewVideoService#getConnectionAnswerHistory).
     */
    List<ReviewVideoWatchSession> findByReviewVideoIdAndStudentIdAndReviewVideoAssignmentIdAndQualifiedTrueAndQuizPassedTrueOrderByQuizCompletedAtAsc(
            Long reviewVideoId, Long studentId, Long reviewVideoAssignmentId);

    /** V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — thêm lọc theo lần giao, mirror ghi chú ReviewVideoProgressRepository. */
    int countByReviewVideoIdAndStudentIdAndReviewVideoAssignmentIdAndQualifiedTrue(Long reviewVideoId, Long studentId, Long reviewVideoAssignmentId);

    /**
     * V83: CONNECTION giờ cần cả xem đạt ngưỡng LẪN đã trả lời đủ câu hỏi CHO ĐÚNG lượt đó mới tính vào
     * viewCount. V129: thêm lọc theo lần giao. V160 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-09-05): đổi điều kiện từ "đã nộp đủ" (quizCompletedAt khác NULL, không phân biệt đúng/sai)
     * sang "đã nộp đủ VÀ đúng 100%" (quizPassed=true) — sai thì không được tính vào viewCount nữa.
     */
    int countByReviewVideoIdAndStudentIdAndReviewVideoAssignmentIdAndQualifiedTrueAndQuizPassedTrue(
            Long reviewVideoId, Long studentId, Long reviewVideoAssignmentId);

    /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — đếm TOÀN BỘ lượt đã có (kể cả chưa qualified) TRƯỚC lượt mới, dùng tính slotIndex theo chu kỳ modulo M. */
    int countByReviewVideoIdAndStudentId(Long reviewVideoId, Long studentId);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — gate "Xóa video" (xem ReviewVideoService#deleteVideo): đã có học sinh xem thì không cho xóa. */
    boolean existsByReviewVideoId(Long reviewVideoId);
}
