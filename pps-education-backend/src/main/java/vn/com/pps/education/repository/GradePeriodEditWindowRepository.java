package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradePeriodEditWindow;

import java.util.Optional;

public interface GradePeriodEditWindowRepository extends JpaRepository<GradePeriodEditWindow, Long> {

    Optional<GradePeriodEditWindow> findBySchoolClassIdAndGradePeriodId(Long classId, Long gradePeriodId);

    boolean existsBySchoolClassIdAndGradePeriodId(Long classId, Long gradePeriodId);
}
