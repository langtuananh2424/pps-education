package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoQuestionSubmission;

import java.util.List;

public interface ReviewVideoQuestionSubmissionRepository extends JpaRepository<ReviewVideoQuestionSubmission, Long> {

    /** Mới nhất trước — dùng .get(0) để lấy attempt mới nhất, cùng pattern với ExerciseAttemptRepository. */
    List<ReviewVideoQuestionSubmission> findByReviewVideoQuestionIdAndStudentIdOrderByAttemptNumberDesc(
            Long reviewVideoQuestionId, Long studentId);

    int countByReviewVideoQuestionIdAndStudentId(Long reviewVideoQuestionId, Long studentId);

    /** UC-23b: Giáo viên xem danh sách bài đã nộp — trả TẤT CẢ attempt (Service tự lọc lấy mới nhất mỗi cặp question+student). */
    List<ReviewVideoQuestionSubmission> findByReviewVideoQuestionIdInAndStudentIdIn(
            List<Long> reviewVideoQuestionIds, List<Long> studentIds);
}
