package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.TuitionPlan;

import java.util.List;
import java.util.Optional;

public interface TuitionPlanRepository extends JpaRepository<TuitionPlan, Long> {

    Optional<TuitionPlan> findByCode(String code);

    List<TuitionPlan> findByCurriculumIdAndStatus(Long curriculumId, TuitionPlan.Status status);
}
