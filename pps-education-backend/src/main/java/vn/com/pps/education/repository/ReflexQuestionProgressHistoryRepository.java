package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReflexQuestionProgressHistory;

import java.util.List;

public interface ReflexQuestionProgressHistoryRepository extends JpaRepository<ReflexQuestionProgressHistory, Long> {
    List<ReflexQuestionProgressHistory> findByReviewVideoAssignmentIdAndStudentIdOrderByCreatedAtAsc(
            Long reviewVideoAssignmentId, Long studentId);

    /** Dùng cho xuất dữ liệu toàn bộ lớp/lần giao (ReviewVideoReportService#exportReflexData). */
    List<ReflexQuestionProgressHistory> findByReviewVideoAssignmentIdOrderByStudentIdAscCreatedAtAsc(
            Long reviewVideoAssignmentId);
}
