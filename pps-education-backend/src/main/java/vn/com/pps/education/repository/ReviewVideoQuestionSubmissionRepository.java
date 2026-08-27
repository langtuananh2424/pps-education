package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoQuestionSubmission;

import java.util.List;

public interface ReviewVideoQuestionSubmissionRepository extends JpaRepository<ReviewVideoQuestionSubmission, Long> {

    /**
     * V69: scoped theo ĐÚNG lần giao (ReviewVideoAssignment) hiện tại —
     * mới nhất trước, dùng .get(0) để lấy attempt mới nhất, cùng pattern
     * với ExerciseAttemptRepository. Không còn tính lịch sử xuyên suốt
     * mọi lần giao (fix bug "Đã nộp bài" hiện sai khi giao lại).
     */
    List<ReviewVideoQuestionSubmission> findByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentIdOrderByAttemptNumberDesc(
            Long reviewVideoQuestionId, Long studentId, Long reviewVideoAssignmentId);

    int countByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentId(
            Long reviewVideoQuestionId, Long studentId, Long reviewVideoAssignmentId);

    /** UC-23b: Giáo viên xem danh sách bài đã nộp — trả TẤT CẢ attempt (Service tự lọc lấy mới nhất mỗi cặp question+student), không lọc theo lần giao (GV cần thấy cả lịch sử cũ để chấm). */
    List<ReviewVideoQuestionSubmission> findByReviewVideoQuestionIdInAndStudentIdIn(
            List<Long> reviewVideoQuestionIds, List<Long> studentIds);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — gate "Xóa video" (xem ReviewVideoService#deleteVideo): câu hỏi REFLEX của video đã có học sinh nộp bài thì không cho xóa. */
    boolean existsByReviewVideoQuestionIdIn(List<Long> reviewVideoQuestionIds);
}
