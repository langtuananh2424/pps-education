package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoWatchSession;

public interface ReviewVideoWatchSessionRepository extends JpaRepository<ReviewVideoWatchSession, Long> {

    int countByReviewVideoIdAndStudentIdAndQualifiedTrue(Long reviewVideoId, Long studentId);

    /** V83: CONNECTION giờ cần cả xem đạt ngưỡng LẪN đã trả lời đủ câu hỏi CHO ĐÚNG lượt đó mới tính vào viewCount. */
    int countByReviewVideoIdAndStudentIdAndQualifiedTrueAndQuizCompletedAtIsNotNull(Long reviewVideoId, Long studentId);

    /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — đếm TOÀN BỘ lượt đã có (kể cả chưa qualified) TRƯỚC lượt mới, dùng tính slotIndex theo chu kỳ modulo M. */
    int countByReviewVideoIdAndStudentId(Long reviewVideoId, Long studentId);
}
