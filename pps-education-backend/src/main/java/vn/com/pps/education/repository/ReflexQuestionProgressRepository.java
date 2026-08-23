package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReflexQuestionProgress;

import java.util.List;
import java.util.Optional;

public interface ReflexQuestionProgressRepository extends JpaRepository<ReflexQuestionProgress, Long> {
    Optional<ReflexQuestionProgress> findByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentId(
            Long reviewVideoQuestionId, Long studentId, Long reviewVideoAssignmentId);

    List<ReflexQuestionProgress> findByReviewVideoAssignmentIdAndStudentId(Long reviewVideoAssignmentId, Long studentId);

    /** V145 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — dùng cho báo cáo GV (ReviewVideoReportService), lấy 1 lần cho cả lớp thay vì N truy vấn/học sinh. */
    List<ReflexQuestionProgress> findByReviewVideoAssignmentIdAndStudentIdIn(Long reviewVideoAssignmentId, List<Long> studentIds);
}
