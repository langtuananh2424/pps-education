package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.TuitionPlanAssignment;

import java.util.List;
import java.util.Optional;

public interface TuitionPlanAssignmentRepository extends JpaRepository<TuitionPlanAssignment, Long> {

    Optional<TuitionPlanAssignment> findBySchoolClassIdAndEffectiveToIsNull(Long classId);

    List<TuitionPlanAssignment> findByEffectiveToIsNull();
}
