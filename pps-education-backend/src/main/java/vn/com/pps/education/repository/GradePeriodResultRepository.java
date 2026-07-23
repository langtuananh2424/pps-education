package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradePeriodResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface GradePeriodResultRepository extends JpaRepository<GradePeriodResult, Long> {

    Optional<GradePeriodResult> findBySchoolClassIdAndStudentIdAndGradePeriodId(Long classId, Long studentId, Long gradePeriodId);

    List<GradePeriodResult> findBySchoolClassIdAndGradePeriodIdOrderByStudentId(Long classId, Long gradePeriodId);

    /** GradeSchedulerService: mọi grade_period_results còn DRAFT của 1 (lớp, kỳ đánh giá). */
    List<GradePeriodResult> findBySchoolClassIdAndGradePeriodIdAndStatus(Long classId, Long gradePeriodId, GradePeriodResult.Status status);

    /** UC-62 A3 — GradeSchedulerService: hết hạn Y ngày phúc khảo kể từ publishedAt, tự động chuyển OFFICIAL bất kể còn PROVISIONAL_PUBLISHED hay APPEAL. */
    List<GradePeriodResult> findByStatusInAndPublishedAtBefore(List<GradePeriodResult.Status> statuses, OffsetDateTime cutoff);

    /** UC-19 (xoá kỳ đánh giá): chặn xoá kỳ còn điểm tổng kết. */
    long countByGradePeriodId(Long gradePeriodId);
}
