package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.TeachingPlan;

import java.util.List;

public interface TeachingPlanRepository extends JpaRepository<TeachingPlan, Long> {
    List<TeachingPlan> findBySchoolClassIdOrderByIdDesc(Long classId);

    List<TeachingPlan> findBySchoolClassIdAndStatusAndVisibleToPartnerTrueOrderByIdDesc(Long classId, TeachingPlan.Status status);
}
