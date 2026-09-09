package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.EntranceAssessmentScore;

import java.util.List;

/** UC-18c (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-28). */
public interface EntranceAssessmentScoreRepository extends JpaRepository<EntranceAssessmentScore, Long> {

    List<EntranceAssessmentScore> findByResultId(Long resultId);

    List<EntranceAssessmentScore> findByResultIdIn(List<Long> resultIds);

    long countByComponentIdAndScoreIsNotNull(Long componentId);

    void deleteByResultId(Long resultId);
}
