package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.EntranceAssessmentComponent;

import java.util.List;

/** UC-18c (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-28). */
public interface EntranceAssessmentComponentRepository extends JpaRepository<EntranceAssessmentComponent, Long> {

    List<EntranceAssessmentComponent> findBySetupIdOrderByDisplayOrderAscIdAsc(Long setupId);

    boolean existsBySetupIdAndCode(Long setupId, String code);

    long countBySetupId(Long setupId);
}
