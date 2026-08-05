package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoWatchSession;

public interface ReviewVideoWatchSessionRepository extends JpaRepository<ReviewVideoWatchSession, Long> {

    int countByReviewVideoIdAndStudentIdAndQualifiedTrue(Long reviewVideoId, Long studentId);

    /** V83: CONNECTION giờ cần cả xem đạt ngưỡng LẪN đã trả lời đủ câu hỏi CHO ĐÚNG lượt đó mới tính vào viewCount. */
    int countByReviewVideoIdAndStudentIdAndQualifiedTrueAndQuizCompletedAtIsNotNull(Long reviewVideoId, Long studentId);
}
