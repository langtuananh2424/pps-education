package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradePeriod;

import java.util.List;
import java.util.Optional;

public interface GradePeriodRepository extends JpaRepository<GradePeriod, Long> {

    List<GradePeriod> findByCurriculumIdOrderByDisplayOrder(Long curriculumId);

    Optional<GradePeriod> findByCurriculumIdAndCode(Long curriculumId, GradePeriod.Code code);

    List<GradePeriod> findByCurriculumIdAndStatus(Long curriculumId, GradePeriod.Status status);
}
