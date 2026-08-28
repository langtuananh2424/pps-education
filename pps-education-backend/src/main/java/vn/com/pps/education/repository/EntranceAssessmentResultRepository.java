package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.EntranceAssessmentResult;

import java.util.List;
import java.util.Optional;

/** UC-18c (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-28). */
public interface EntranceAssessmentResultRepository extends JpaRepository<EntranceAssessmentResult, Long> {

    List<EntranceAssessmentResult> findBySetupIdOrderByAssessedDateDescIdDesc(Long setupId);

    Optional<EntranceAssessmentResult> findBySetupIdAndLeadId(Long setupId, Long leadId);

    Optional<EntranceAssessmentResult> findBySetupIdAndStudentId(Long setupId, Long studentId);

    long countBySetupId(Long setupId);
}
