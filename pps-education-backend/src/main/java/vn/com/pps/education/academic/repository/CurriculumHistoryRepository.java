package vn.com.pps.education.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.academic.domain.CurriculumHistory;

import java.util.List;

public interface CurriculumHistoryRepository extends JpaRepository<CurriculumHistory, Long> {
    List<CurriculumHistory> findByCurriculumIdOrderByCreatedAtDesc(Long curriculumId);
}
