package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradePeriodResult;

import java.util.List;
import java.util.Optional;

public interface GradePeriodResultRepository extends JpaRepository<GradePeriodResult, Long> {

    Optional<GradePeriodResult> findBySchoolClassIdAndStudentIdAndGradePeriodId(Long classId, Long studentId, Long gradePeriodId);

    List<GradePeriodResult> findBySchoolClassIdAndGradePeriodIdOrderByStudentId(Long classId, Long gradePeriodId);

    /** UC-19 (xoá kỳ đánh giá): chặn xoá kỳ còn điểm tổng kết. */
    long countByGradePeriodId(Long gradePeriodId);
}
