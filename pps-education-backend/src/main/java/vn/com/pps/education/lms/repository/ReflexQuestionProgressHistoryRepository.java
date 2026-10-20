package vn.com.pps.education.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.lms.domain.ReflexQuestionProgressHistory;

import java.util.List;

public interface ReflexQuestionProgressHistoryRepository extends JpaRepository<ReflexQuestionProgressHistory, Long> {
    List<ReflexQuestionProgressHistory> findByReviewVideoAssignmentIdAndStudentIdOrderByCreatedAtAsc(
            Long reviewVideoAssignmentId, Long studentId);

    /** Dùng cho xuất dữ liệu toàn bộ lớp/lần giao (ReviewVideoReportService#exportReflexData). */
    List<ReflexQuestionProgressHistory> findByReviewVideoAssignmentIdOrderByStudentIdAscCreatedAtAsc(
            Long reviewVideoAssignmentId);

    /** V217 — mọi lần nộp của 1 bước (viết/nói) của 1 tiến trình, cũ trước; dùng chọn/chấm lại lần tốt nhất. */
    List<ReflexQuestionProgressHistory> findByReflexQuestionProgressIdAndAttemptTypeOrderByAttemptNumberAsc(
            Long reflexQuestionProgressId, ReflexQuestionProgressHistory.AttemptType attemptType);
}
