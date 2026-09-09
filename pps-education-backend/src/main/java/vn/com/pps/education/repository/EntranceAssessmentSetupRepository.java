package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.EntranceAssessmentSetup;

import java.util.List;
import java.util.Optional;

/** UC-18c (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-28). */
public interface EntranceAssessmentSetupRepository extends JpaRepository<EntranceAssessmentSetup, Long> {

    List<EntranceAssessmentSetup> findBySiteIdAndDeletedAtIsNullOrderByIdDesc(Long siteId);

    List<EntranceAssessmentSetup> findBySiteIdAndAcademicYearIdAndDeletedAtIsNullOrderByIdDesc(Long siteId, Long academicYearId);

    Optional<EntranceAssessmentSetup> findByIdAndDeletedAtIsNull(Long id);

    boolean existsBySiteIdAndAcademicYearIdAndNameAndDeletedAtIsNull(Long siteId, Long academicYearId, String name);
}
