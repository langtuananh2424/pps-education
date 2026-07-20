package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradePeriodResult;

import java.util.List;
import java.util.Optional;

public interface GradePeriodResultRepository extends JpaRepository<GradePeriodResult, Long> {

    Optional<GradePeriodResult> findBySchoolClassIdAndStudentIdAndGradePeriodId(Long classId, Long studentId, Long gradePeriodId);

    List<GradePeriodResult> findBySchoolClassIdAndGradePeriodIdOrderByStudentId(Long classId, Long gradePeriodId);

    /** GradeSchedulerService: mọi grade_period_results còn DRAFT của 1 (lớp, kỳ đánh giá). */
    List<GradePeriodResult> findBySchoolClassIdAndGradePeriodIdAndStatus(Long classId, Long gradePeriodId, GradePeriodResult.Status status);
}
