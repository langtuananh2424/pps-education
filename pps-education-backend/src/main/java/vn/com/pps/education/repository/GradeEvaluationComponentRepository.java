package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradeEvaluationComponent;

import java.util.List;
import java.util.Optional;

public interface GradeEvaluationComponentRepository extends JpaRepository<GradeEvaluationComponent, Long> {

    List<GradeEvaluationComponent> findByGradeComponentSetupIdOrderByDisplayOrder(Long gradeComponentSetupId);

    Optional<GradeEvaluationComponent> findByGradeComponentSetupIdAndCode(
            Long gradeComponentSetupId, GradeEvaluationComponent.ComponentCode code);

    /** UC-19 (xoá setup rỗng): chặn xoá setup còn thành phần điểm. */
    long countByGradeComponentSetupId(Long gradeComponentSetupId);
}
