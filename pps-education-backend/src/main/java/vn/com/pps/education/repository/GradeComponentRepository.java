package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradeComponent;

import java.util.List;
import java.util.Optional;

public interface GradeComponentRepository extends JpaRepository<GradeComponent, Long> {

    List<GradeComponent> findByGradePeriodIdOrderByDisplayOrder(Long gradePeriodId);

    Optional<GradeComponent> findByGradePeriodIdAndCode(Long gradePeriodId, GradeComponent.ComponentCode code);

    /** UC-19 (xoá kỳ đánh giá): chặn xoá kỳ còn thành phần điểm. */
    long countByGradePeriodId(Long gradePeriodId);
}
