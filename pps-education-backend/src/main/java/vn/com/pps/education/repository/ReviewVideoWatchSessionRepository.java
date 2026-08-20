package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoWatchSession;

public interface ReviewVideoWatchSessionRepository extends JpaRepository<ReviewVideoWatchSession, Long> {

    /** V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — thêm lọc theo lần giao, mirror ghi chú ReviewVideoProgressRepository. */
    int countByReviewVideoIdAndStudentIdAndReviewVideoAssignmentIdAndQualifiedTrue(Long reviewVideoId, Long studentId, Long reviewVideoAssignmentId);

    /** V83: CONNECTION giờ cần cả xem đạt ngưỡng LẪN đã trả lời đủ câu hỏi CHO ĐÚNG lượt đó mới tính vào viewCount. V129: thêm lọc theo lần giao. */
    int countByReviewVideoIdAndStudentIdAndReviewVideoAssignmentIdAndQualifiedTrueAndQuizCompletedAtIsNotNull(
            Long reviewVideoId, Long studentId, Long reviewVideoAssignmentId);

    /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — đếm TOÀN BỘ lượt đã có (kể cả chưa qualified) TRƯỚC lượt mới, dùng tính slotIndex theo chu kỳ modulo M. */
    int countByReviewVideoIdAndStudentId(Long reviewVideoId, Long studentId);
}
